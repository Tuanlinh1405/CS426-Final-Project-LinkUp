#!/usr/bin/env python3
"""Prepare Creative Commons YouTube videos and seed them through LinkUp's Reels API.

The script deliberately uploads through POST /reels instead of writing directly to
Supabase Storage. That keeps the video object, reel_assets row, and reels metadata
consistent. Only use source videos you are legally allowed to download and reuse.
"""

from __future__ import annotations

import argparse
import csv
import getpass
import json
import math
import os
import re
import shutil
import subprocess
import sys
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable

try:
    import requests
except ImportError:  # Keep --help readable before dependencies are installed.
    requests = None  # type: ignore[assignment]


YOUTUBE_SEARCH_URL = "https://www.googleapis.com/youtube/v3/search"
YOUTUBE_VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos"
MAX_VIDEO_BYTES = 50 * 1024 * 1024
TARGET_VIDEO_BYTES = 46 * 1024 * 1024
MAX_THUMBNAIL_BYTES = 1024 * 1024
DEFAULT_COUNT = 16

# Queries intentionally bias toward reusable/original material. The API request also
# enforces videoLicense=creativeCommon and every result is verified with videos.list.
DEFAULT_TOPICS: dict[str, str] = {
    "football": "football professional match highlights creative commons full video",
    "gaming": "League of Legends gameplay highlights creative commons full video",
    "music": "Vietnamese independent music creative commons official music video",
    "technology": "technology coding tutorial creative commons",
    "travel_food": "travel food street video creative commons",
}


@dataclass(frozen=True)
class Candidate:
    video_id: str
    topic: str
    title: str
    channel: str
    source_url: str
    duration_seconds: float
    published_at: str
    license: str = "creativeCommon"


def fail(message: str) -> None:
    raise SystemExit(f"Error: {message}")


def require_requests() -> None:
    if requests is None:
        fail(
            "Missing Python dependencies. Run: "
            "python -m pip install -r scripts/requirements-reel-seeder.txt"
        )


def require_downloader() -> None:
    result = subprocess.run(
        [sys.executable, "-m", "yt_dlp", "--version"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        fail(
            "yt-dlp is not installed. Run: "
            "python -m pip install -r scripts/requirements-reel-seeder.txt"
        )


def require_media_tools() -> None:
    missing = [name for name in ("ffmpeg", "ffprobe") if shutil.which(name) is None]
    if missing:
        fail(
            f"Missing {', '.join(missing)}. Install FFmpeg and open a new terminal. "
            "On Windows: winget install Gyan.FFmpeg"
        )


def parse_iso8601_duration(value: str) -> float:
    match = re.fullmatch(
        r"P(?:(?P<days>\d+)D)?T(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?(?P<seconds>\d+(?:\.\d+)?)S",
        value,
    )
    if not match:
        raise ValueError(f"Unsupported ISO-8601 duration: {value}")
    return (
        float(match.group("days") or 0) * 86400
        + float(match.group("hours") or 0) * 3600
        + float(match.group("minutes") or 0) * 60
        + float(match.group("seconds") or 0)
    )


def youtube_get(url: str, api_key: str, **params: Any) -> dict[str, Any]:
    assert requests is not None
    response = requests.get(url, params={**params, "key": api_key}, timeout=30)
    if response.status_code != 200:
        try:
            detail = response.json().get("error", {}).get("message", response.text)
        except ValueError:
            detail = response.text
        fail(f"YouTube Data API returned {response.status_code}: {detail}")
    return response.json()


def discover_candidates(
    api_key: str,
    count: int,
    topics: dict[str, str],
    min_duration_seconds: float,
    max_duration_seconds: float,
) -> list[Candidate]:
    per_topic = max(8, math.ceil(count / len(topics)) + 6)
    discovered: dict[str, tuple[str, dict[str, Any]]] = {}

    for topic, query in topics.items():
        payload = youtube_get(
            YOUTUBE_SEARCH_URL,
            api_key,
            part="snippet",
            q=query,
            type="video",
            maxResults=min(50, per_topic),
            safeSearch="strict",
            videoDuration="medium",
            videoEmbeddable="true",
            videoLicense="creativeCommon",
        )
        for item in payload.get("items", []):
            video_id = item.get("id", {}).get("videoId")
            if video_id and video_id not in discovered:
                discovered[video_id] = (topic, item.get("snippet", {}))

    if not discovered:
        fail("No Creative Commons candidates were returned by YouTube.")

    details: dict[str, dict[str, Any]] = {}
    ids = list(discovered)
    for offset in range(0, len(ids), 50):
        payload = youtube_get(
            YOUTUBE_VIDEOS_URL,
            api_key,
            part="snippet,contentDetails,status",
            id=",".join(ids[offset : offset + 50]),
            maxResults=50,
        )
        details.update({item["id"]: item for item in payload.get("items", [])})

    by_topic: dict[str, list[Candidate]] = {topic: [] for topic in topics}
    for video_id, (topic, search_snippet) in discovered.items():
        item = details.get(video_id)
        if not item:
            continue
        status = item.get("status", {})
        if (
            status.get("license") != "creativeCommon"
            or status.get("privacyStatus") != "public"
            or status.get("embeddable") is False
        ):
            continue
        try:
            duration = parse_iso8601_duration(item["contentDetails"]["duration"])
        except (KeyError, ValueError):
            continue
        if duration < min_duration_seconds or (max_duration_seconds > 0 and duration > max_duration_seconds):
            continue
        snippet = item.get("snippet", search_snippet)
        by_topic[topic].append(
            Candidate(
                video_id=video_id,
                topic=topic,
                title=snippet.get("title", video_id),
                channel=snippet.get("channelTitle", "Unknown creator"),
                source_url=f"https://www.youtube.com/watch?v={video_id}",
                duration_seconds=duration,
                published_at=snippet.get("publishedAt", ""),
            )
        )

    # Round-robin keeps the seed set balanced instead of filling it with one topic.
    ordered: list[Candidate] = []
    topic_names = list(topics)
    index = 0
    while True:
        added = False
        for topic in topic_names:
            candidates = by_topic[topic]
            if index < len(candidates):
                ordered.append(candidates[index])
                added = True
        if not added:
            break
        index += 1
    return ordered


def run(command: list[str]) -> None:
    process = subprocess.run(command)
    if process.returncode != 0:
        raise RuntimeError(f"Command failed with exit code {process.returncode}: {command[0]}")


def download_video(candidate: Candidate, raw_dir: Path) -> Path:
    output_template = str(raw_dir / f"{candidate.video_id}.%(ext)s")
    run(
        [
            sys.executable,
            "-m",
            "yt_dlp",
            "--no-playlist",
            "--no-write-comments",
            "--no-write-subs",
            "--no-progress",
            "--format",
            "bv*[height<=720]+ba/b[height<=720]",
            "--merge-output-format",
            "mp4",
            "--output",
            output_template,
            candidate.source_url,
        ]
    )
    matches = [
        path
        for path in raw_dir.glob(f"{candidate.video_id}.*")
        if path.suffix not in {".part", ".ytdl"} and path.is_file()
    ]
    if not matches:
        raise RuntimeError("yt-dlp completed but no media file was created")
    return max(matches, key=lambda path: path.stat().st_mtime)


def encode_video(source: Path, destination: Path, source_duration: float) -> None:
    # Reserve space for MP4 overhead and audio, then spend the remaining budget on video.
    # There is no duration cap: longer inputs simply receive a lower bitrate.
    total_kbps = max(160, int(TARGET_VIDEO_BYTES * 8 / max(source_duration, 1) / 1000))
    audio_kbps = 96 if total_kbps >= 320 else 48
    video_kbps = max(80, total_kbps - audio_kbps - 24)
    video_filter = (
        "scale=540:960:force_original_aspect_ratio=decrease,"
        "pad=540:960:(ow-iw)/2:(oh-ih)/2:black,setsar=1"
    )

    for _ in range(3):
        command = [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-vf",
            video_filter,
            "-r",
            "30",
            "-c:v",
            "libx264",
            "-preset",
            "superfast",
            "-b:v",
            f"{video_kbps}k",
            "-maxrate",
            f"{max(96, int(video_kbps * 1.15))}k",
            "-bufsize",
            f"{max(192, video_kbps * 2)}k",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            f"{audio_kbps}k",
            "-ar",
            "48000",
            "-movflags",
            "+faststart",
            "-map_metadata",
            "-1",
            str(destination),
        ]
        run(command)
        actual_size = destination.stat().st_size
        if actual_size <= MAX_VIDEO_BYTES:
            return
        video_kbps = max(32, int(video_kbps * MAX_VIDEO_BYTES / actual_size * 0.88))
    raise RuntimeError("Cannot compress this video under 50 MiB without an unusable bitrate")


def create_thumbnail(video: Path, destination: Path) -> None:
    run(
        [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-ss",
            "1",
            "-i",
            str(video),
            "-frames:v",
            "1",
            "-q:v",
            "5",
            str(destination),
        ]
    )
    if destination.stat().st_size > MAX_THUMBNAIL_BYTES:
        raise RuntimeError("Generated thumbnail exceeds 1 MiB")


def probe_video(path: Path) -> dict[str, Any]:
    process = subprocess.run(
        [
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=codec_name,width,height:format=duration,size",
            "-of",
            "json",
            str(path),
        ],
        capture_output=True,
        text=True,
    )
    if process.returncode != 0:
        raise RuntimeError(f"ffprobe could not inspect {path.name}: {process.stderr.strip()}")
    data = json.loads(process.stdout)
    stream = (data.get("streams") or [{}])[0]
    media_format = data.get("format", {})
    result = {
        "codec": stream.get("codec_name"),
        "width": int(stream.get("width", 0)),
        "height": int(stream.get("height", 0)),
        "duration_seconds": float(media_format.get("duration", 0)),
        "size_bytes": int(media_format.get("size", path.stat().st_size)),
    }
    if result["codec"] != "h264":
        raise RuntimeError("Prepared video is not H.264")
    if result["duration_seconds"] <= 0:
        raise RuntimeError("Prepared video duration is invalid")
    if not (0 < result["size_bytes"] <= MAX_VIDEO_BYTES):
        raise RuntimeError("Prepared video exceeds the backend 50 MiB limit")
    if not (0 < result["width"] <= 4096 and 0 < result["height"] <= 4096):
        raise RuntimeError("Prepared video dimensions are invalid")
    return result


def write_caption_files(path: Path, entries: list[dict[str, Any]]) -> None:
    csv_path = path.with_name("captions.csv")
    with csv_path.open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=["video_id", "topic", "caption", "source_url", "channel", "license", "video_path"])
        writer.writeheader()
        for entry in entries:
            writer.writerow({name: entry.get(name, "") for name in writer.fieldnames})
    text = "\n\n".join(
        f"[{entry.get('video_id', '')}]\n{entry.get('caption', '')}" for entry in entries
    )
    path.with_name("captions.txt").write_text(text + ("\n" if text else ""), encoding="utf-8")


def write_manifest(path: Path, entries: list[dict[str, Any]]) -> None:
    temporary = path.with_suffix(".tmp")
    temporary.write_text(
        json.dumps({"version": 1, "items": entries}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(path)
    write_caption_files(path, entries)


def suggested_caption(candidate: Candidate) -> str:
    prompts = {
        "football": "Những khoảnh khắc đáng chú ý trên sân cỏ ⚽ Bạn ấn tượng pha nào nhất?",
        "gaming": "Highlight trận đấu hôm nay 🎮 Pha xử lý nào đáng xem lại nhất?",
        "music": "Một chút âm nhạc cho ngày mới 🎧 Bạn nghe đoạn nào nhiều nhất?",
        "technology": "Một góc công nghệ đáng thử 💻 Bạn đã biết mẹo này chưa?",
        "travel_food": "Đi một vòng khám phá địa điểm và món ăn mới ✈️ Bạn muốn thử không?",
    }
    return (
        f"{prompts.get(candidate.topic, 'Một video đáng xem hôm nay ✨')}\n\n"
        f"{candidate.title}\n"
        f"Nguồn: {candidate.source_url}\n"
        f"Tác giả: {candidate.channel} · Creative Commons"
    )[:2200]


def load_manifest(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        fail(f"Manifest not found: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("version") != 1 or not isinstance(data.get("items"), list):
        fail("Unsupported or invalid manifest file.")
    return data["items"]


def prepare_videos(
    candidates: Iterable[Candidate],
    count: int,
    output_dir: Path,
    keep_raw: bool,
) -> tuple[list[dict[str, Any]], Path]:
    raw_dir = output_dir / "raw"
    prepared_dir = output_dir / "prepared"
    raw_dir.mkdir(parents=True, exist_ok=True)
    prepared_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = output_dir / "manifest.json"
    entries = load_manifest(manifest_path) if manifest_path.exists() else []
    prepared_ids = {str(entry.get("video_id")) for entry in entries}
    if entries:
        print(f"Resuming with {len(entries)} prepared video(s).")

    for candidate in candidates:
        if len(entries) >= count:
            break
        if candidate.video_id in prepared_ids:
            continue
        print(f"[{len(entries) + 1}/{count}] {candidate.topic}: {candidate.title}")
        raw_path: Path | None = None
        try:
            raw_path = download_video(candidate, raw_dir)
            video_path = prepared_dir / f"{candidate.video_id}.mp4"
            thumbnail_path = prepared_dir / f"{candidate.video_id}.jpg"
            encode_video(raw_path, video_path, candidate.duration_seconds)
            create_thumbnail(video_path, thumbnail_path)
            media = probe_video(video_path)
            entry = {
                **asdict(candidate),
                "video_path": str(video_path.resolve()),
                "thumbnail_path": str(thumbnail_path.resolve()),
                "media": media,
                "caption": suggested_caption(candidate),
                "reviewed": False,
                "upload_status": "pending_review",
            }
            entries.append(entry)
            prepared_ids.add(candidate.video_id)
            write_manifest(manifest_path, entries)
        except Exception as error:
            print(f"  Skipped: {error}", file=sys.stderr)
        finally:
            if raw_path and raw_path.exists() and not keep_raw:
                raw_path.unlink()

    if len(entries) < count:
        fail(
            f"Only prepared {len(entries)}/{count} videos. Review the errors, then rerun; "
            "YouTube availability can vary by region and time."
        )
    return entries, manifest_path


def parse_dotenv_value(path: Path, key: str) -> str | None:
    if not path.exists():
        return None
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        if name.strip() == key:
            return value.strip().strip('"').strip("'")
    return None


def configure_media_path(config: Path) -> None:
    media_dir = parse_dotenv_value(config, "FFMPEG_DIR")
    if media_dir:
        directory = str(Path(media_dir).expanduser())
        os.environ["PATH"] = directory + os.pathsep + os.environ.get("PATH", "")


def require_supabase_storage(storage_env: Path) -> None:
    configured = os.environ.get("REELS_STORAGE") or parse_dotenv_value(storage_env, "REELS_STORAGE")
    if configured != "supabase":
        fail(
            f"REELS_STORAGE must be supabase before upload. Check {storage_env}; "
            "the script will not upload directly to the bucket."
        )


def get_backend_token(args: argparse.Namespace) -> str:
    token = args.token or os.environ.get("LINKUP_TOKEN")
    if token:
        return token
    assert requests is not None
    identity = os.environ.get("LINKUP_EMAIL_OR_USERNAME") or input("LinkUp email/username: ").strip()
    password = os.environ.get("LINKUP_PASSWORD") or getpass.getpass("LinkUp password: ")
    response = requests.post(
        f"{args.backend_url.rstrip('/')}/auth/login",
        json={"emailOrUsername": identity, "password": password},
        timeout=30,
    )
    if response.status_code != 200:
        fail(f"LinkUp login failed ({response.status_code}): {response.text[:300]}")
    token = response.json().get("token")
    if not token:
        fail("Login response did not contain a token.")
    return token


def upload_entries(
    entries: list[dict[str, Any]],
    manifest_path: Path,
    args: argparse.Namespace,
) -> None:
    assert requests is not None
    require_supabase_storage(args.storage_env)
    token = get_backend_token(args)
    backend_url = args.backend_url.rstrip("/")
    uploaded = 0

    for index, entry in enumerate(entries, start=1):
        if entry.get("upload_status") == "uploaded":
            continue
        if not entry.get("reviewed") and not args.upload_unreviewed:
            print(f"[{index}] Waiting for review: {entry['title']}")
            continue
        reel_id = entry.get("reel_id") or str(uuid.uuid4())
        entry["reel_id"] = reel_id
        caption = entry.get("caption") or (
            f"{entry['title']}\nNguồn: {entry['source_url']}\n"
            f"Tác giả: {entry['channel']} · Creative Commons"
        )[:2200]
        video_path = Path(entry["video_path"])
        thumbnail_path = Path(entry["thumbnail_path"])
        if not video_path.exists() or not thumbnail_path.exists():
            entry["upload_status"] = "failed"
            entry["upload_error"] = "Prepared media file is missing"
            write_manifest(manifest_path, entries)
            continue
        print(f"[{index}/{len(entries)}] Uploading {video_path.name}")
        try:
            with video_path.open("rb") as video, thumbnail_path.open("rb") as thumbnail:
                response = requests.post(
                    f"{backend_url}/reels",
                    headers={"Authorization": f"Bearer {token}"},
                    data={"id": reel_id, "caption": caption},
                    files={
                        "video": (video_path.name, video, "video/mp4"),
                        "thumbnail": (thumbnail_path.name, thumbnail, "image/jpeg"),
                    },
                    timeout=(15, 300),
                )
            if response.status_code not in (200, 201):
                raise RuntimeError(f"HTTP {response.status_code}: {response.text[:500]}")
            entry["upload_status"] = "uploaded"
            entry.pop("upload_error", None)
            uploaded += 1
        except Exception as error:
            entry["upload_status"] = "failed"
            entry["upload_error"] = str(error)
            print(f"  Upload failed: {error}", file=sys.stderr)
        finally:
            write_manifest(manifest_path, entries)
    print(f"Uploaded {uploaded} reel(s). Manifest: {manifest_path}")


def select_topics(value: str) -> dict[str, str]:
    names = [item.strip() for item in value.split(",") if item.strip()]
    unknown = [name for name in names if name not in DEFAULT_TOPICS]
    if unknown:
        fail(f"Unknown topics: {', '.join(unknown)}. Available: {', '.join(DEFAULT_TOPICS)}")
    if not names:
        fail("Select at least one topic.")
    return {name: DEFAULT_TOPICS[name] for name in names}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Prepare 15-20 licensed YouTube videos and seed LinkUp Reels via its backend API."
    )
    parser.add_argument("--count", type=int, default=DEFAULT_COUNT, help="Total videos (15-20, default: 16).")
    parser.add_argument(
        "--topics",
        default=",".join(DEFAULT_TOPICS),
        help=f"Comma-separated topics: {', '.join(DEFAULT_TOPICS)}",
    )
    parser.add_argument("--output", type=Path, default=Path(".reel-seed-long"))
    parser.add_argument("--min-duration", type=float, default=180, help="Minimum source duration in seconds (default: 180).")
    parser.add_argument("--max-duration", type=float, default=600, help="Maximum seed duration in seconds; use 0 for no cap (default: 600).")
    parser.add_argument("--api-key", default=os.environ.get("YOUTUBE_API_KEY"))
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("scripts/.env.reel-seeder"),
        help="Local ignored config containing YOUTUBE_API_KEY.",
    )
    parser.add_argument("--keep-raw", action="store_true", help="Keep original downloads after encoding.")
    parser.add_argument("--upload", action="store_true", help="Upload after preparation.")
    parser.add_argument("--upload-only", action="store_true", help="Upload an existing output/manifest.json.")
    parser.add_argument(
        "--upload-unreviewed",
        action="store_true",
        help="Upload without manually setting reviewed=true in manifest.json.",
    )
    parser.add_argument("--backend-url", default="http://127.0.0.1:8080")
    parser.add_argument("--token", help="LinkUp JWT; prefer LINKUP_TOKEN to avoid shell history.")
    parser.add_argument("--storage-env", type=Path, default=Path("backend/.env.storage"))
    parser.add_argument(
        "--acknowledge-rights",
        action="store_true",
        help="Confirm you will only download/reuse videos for which you have permission.",
    )
    parser.add_argument("--check", action="store_true", help="Only check local dependencies/configuration.")
    return parser


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    args = build_parser().parse_args()
    if args.count < 15 or args.count > 20:
        fail("--count must be between 15 and 20.")
    require_requests()
    configure_media_path(args.config)
    if args.check:
        require_downloader()
        require_media_tools()
        print("Python dependencies, yt-dlp, FFmpeg and FFprobe are available.")
        if args.storage_env.exists():
            configured = os.environ.get("REELS_STORAGE") or parse_dotenv_value(args.storage_env, "REELS_STORAGE")
            print(f"Backend storage mode: {configured or 'not configured'}")
        return
    if not args.acknowledge_rights:
        fail(
            "Pass --acknowledge-rights after confirming you may download and reuse the selected videos. "
            "The script filters Creative Commons metadata, but you must review the manifest before publishing."
        )

    args.output = args.output.resolve()
    manifest_path = args.output / "manifest.json"
    if args.upload_only:
        entries = load_manifest(manifest_path)
        upload_entries(entries, manifest_path, args)
        return
    require_downloader()
    require_media_tools()
    args.api_key = args.api_key or parse_dotenv_value(args.config, "YOUTUBE_API_KEY")
    if not args.api_key:
        fail(f"Set YOUTUBE_API_KEY, pass --api-key, or add it to {args.config}. Do not commit the key.")

    topics = select_topics(args.topics)
    if args.min_duration <= 0:
        fail("--min-duration must be greater than 0.")
    if args.max_duration < 0 or (args.max_duration > 0 and args.max_duration < args.min_duration):
        fail("--max-duration must be 0 or greater than/equal to --min-duration.")
    candidates = discover_candidates(args.api_key, args.count, topics, args.min_duration, args.max_duration)
    entries, manifest_path = prepare_videos(candidates, args.count, args.output, args.keep_raw)
    print(f"Prepared {len(entries)} videos. Review licensing/source details in {manifest_path}")
    if args.upload:
        upload_entries(entries, manifest_path, args)


if __name__ == "__main__":
    main()
