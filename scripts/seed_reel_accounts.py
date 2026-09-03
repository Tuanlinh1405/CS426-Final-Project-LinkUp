#!/usr/bin/env python3
"""Create local LinkUp seed accounts and distribute prepared Reels between them.

Credentials are written under the ignored seed output directory. Upload identifiers
are deterministic, so rerunning this command resumes safely without duplicate Reels.
"""

from __future__ import annotations

import argparse
import json
import secrets
import sys
import uuid
from pathlib import Path
from typing import Any

try:
    import requests
except ImportError:
    requests = None  # type: ignore[assignment]


ACCOUNT_NAMES = (
    "Minh Anh",
    "Gia Huy",
    "Thao Nguyen",
    "Quang Minh",
    "Bao Tran",
    "Hoang Nam",
    "Ngoc Han",
    "Duc Anh",
    "Khanh Linh",
    "Tuan Kiet",
)


def fail(message: str) -> None:
    raise SystemExit(f"Error: {message}")


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def load_manifest(path: Path) -> dict[str, Any]:
    if not path.exists():
        fail(f"Manifest not found: {path}")
    manifest = json.loads(path.read_text(encoding="utf-8"))
    if manifest.get("version") != 1 or not isinstance(manifest.get("items"), list):
        fail(f"Invalid manifest: {path}")
    if not manifest["items"]:
        fail("Manifest does not contain any videos.")
    return manifest


def load_or_create_accounts(path: Path, count: int) -> dict[str, Any]:
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
        accounts = data.get("accounts")
        if not isinstance(accounts, list) or len(accounts) != count:
            fail(
                f"{path} contains a different account count. Move it aside before "
                "starting a new seed group."
            )
        return data

    accounts = []
    for index in range(1, count + 1):
        accounts.append(
            {
                "email": f"linkup.seed.{index:02d}@example.com",
                "username": f"linkup_seed_{index:02d}",
                "password": "Seed!" + secrets.token_urlsafe(18),
                "fullName": ACCOUNT_NAMES[(index - 1) % len(ACCOUNT_NAMES)],
                "status": "pending",
            }
        )
    data = {
        "warning": "LOCAL TEST CREDENTIALS - this ignored file must not be committed",
        "accounts": accounts,
    }
    # Save passwords before the first request so an interrupted run remains resumable.
    write_json(path, data)
    return data


def response_detail(response: Any) -> str:
    text = (response.text or "").replace("\n", " ").strip()
    return text[:500]


def login_or_register(base_url: str, account: dict[str, Any]) -> tuple[str, str]:
    assert requests is not None
    login = requests.post(
        f"{base_url}/auth/login",
        json={"emailOrUsername": account["username"], "password": account["password"]},
        timeout=30,
    )
    response = login
    if login.status_code == 401:
        response = requests.post(
            f"{base_url}/auth/register",
            json={
                "email": account["email"],
                "username": account["username"],
                "password": account["password"],
                "fullName": account["fullName"],
            },
            timeout=30,
        )
    if response.status_code not in (200, 201):
        fail(
            f"Cannot access account {account['username']} "
            f"(HTTP {response.status_code}): {response_detail(response)}"
        )
    payload = response.json()
    token = payload.get("token")
    user_id = payload.get("user", {}).get("id")
    if not token or not user_id:
        fail(f"Invalid auth response for {account['username']}.")
    return str(token), str(user_id)


def upload_reel(
    base_url: str,
    token: str,
    account: dict[str, Any],
    entry: dict[str, Any],
) -> dict[str, Any]:
    assert requests is not None
    video_path = Path(entry["video_path"])
    thumbnail_path = Path(entry["thumbnail_path"])
    if not video_path.is_file() or not thumbnail_path.is_file():
        fail(f"Prepared media is missing for {entry.get('video_id', video_path.name)}.")

    reel_id = str(
        uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"https://linkup.local/seeds/{account['username']}/{entry['video_id']}",
        )
    )
    caption = str(entry.get("caption") or entry.get("title") or "")[:2200]
    with video_path.open("rb") as video, thumbnail_path.open("rb") as thumbnail:
        response = requests.post(
            f"{base_url}/reels",
            headers={"Authorization": f"Bearer {token}"},
            data={"id": reel_id, "caption": caption},
            files={
                "video": (video_path.name, video, "video/mp4"),
                "thumbnail": (thumbnail_path.name, thumbnail, "image/jpeg"),
            },
            timeout=(20, 600),
        )
    if response.status_code not in (200, 201):
        raise RuntimeError(f"HTTP {response.status_code}: {response_detail(response)}")
    reel = response.json()
    if reel.get("id") != reel_id or reel.get("author", {}).get("id") != account["id"]:
        raise RuntimeError("Backend returned a Reel with an unexpected id or author.")
    return reel


def verify_accounts(base_url: str, accounts: list[dict[str, Any]], expected: dict[str, int]) -> None:
    assert requests is not None
    for account in accounts:
        token, user_id = login_or_register(base_url, account)
        response = requests.get(
            f"{base_url}/reels",
            headers={"Authorization": f"Bearer {token}"},
            params={"authorId": user_id, "limit": 30},
            timeout=30,
        )
        if response.status_code != 200:
            fail(f"Cannot verify {account['username']}: HTTP {response.status_code}.")
        items = response.json().get("items", [])
        actual_ids = {item.get("id") for item in items}
        expected_ids = {
            reel_id
            for key, reel_id in expected.items()
            if key.startswith(account["username"] + ":")
        }
        missing = expected_ids - actual_ids
        if missing:
            fail(f"Verification failed for {account['username']}: {len(missing)} Reel(s) missing.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Create LinkUp seed users and upload prepared Reels.")
    parser.add_argument("--backend-url", default="http://127.0.0.1:8080")
    parser.add_argument("--output", type=Path, default=Path(".reel-seed-long"))
    parser.add_argument("--accounts", type=int, default=10)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if requests is None:
        fail("Install dependencies: python -m pip install -r scripts/requirements-reel-seeder.txt")
    if args.accounts < 1 or args.accounts > len(ACCOUNT_NAMES):
        fail(f"--accounts must be between 1 and {len(ACCOUNT_NAMES)}.")

    base_url = args.backend_url.rstrip("/")
    try:
        health = requests.get(base_url + "/", timeout=10)
    except requests.RequestException as error:
        fail(f"Backend is not reachable at {base_url}: {error}")
    if health.status_code != 200:
        fail(f"Backend health check returned HTTP {health.status_code}.")

    manifest_path = args.output / "manifest.json"
    accounts_path = args.output / "accounts.json"
    manifest = load_manifest(manifest_path)
    account_data = load_or_create_accounts(accounts_path, args.accounts)
    accounts: list[dict[str, Any]] = account_data["accounts"]

    tokens: dict[str, str] = {}
    for index, account in enumerate(accounts, start=1):
        token, user_id = login_or_register(base_url, account)
        account["id"] = user_id
        account["status"] = "ready"
        tokens[account["username"]] = token
        write_json(accounts_path, account_data)
        print(f"[{index}/{len(accounts)}] Account ready: {account['username']}")

    entries: list[dict[str, Any]] = manifest["items"]
    expected: dict[str, str] = {}
    uploaded = 0
    resumed = 0
    for index, entry in enumerate(entries, start=1):
        account = accounts[(index - 1) % len(accounts)]
        reel_id = str(
            uuid.uuid5(
                uuid.NAMESPACE_URL,
                f"https://linkup.local/seeds/{account['username']}/{entry['video_id']}",
            )
        )
        expected[f"{account['username']}:{entry['video_id']}"] = reel_id
        entry["seed_username"] = account["username"]
        entry["seed_user_id"] = account["id"]
        entry["reel_id"] = reel_id
        print(f"[{index}/{len(entries)}] Uploading {entry['video_id']} as {account['username']}")
        try:
            reel = upload_reel(base_url, tokens[account["username"]], account, entry)
            if reel.get("createdAt") and entry.get("account_seed_status") == "uploaded":
                resumed += 1
            else:
                uploaded += 1
            entry["account_seed_status"] = "uploaded"
            entry["upload_status"] = "uploaded"
            entry.pop("upload_error", None)
        except Exception as error:
            entry["account_seed_status"] = "failed"
            entry["upload_error"] = str(error)
            write_json(manifest_path, manifest)
            print(f"  Failed: {error}", file=sys.stderr)
            fail("Upload stopped. Fix the issue and rerun the same command to resume.")
        write_json(manifest_path, manifest)

    verify_accounts(base_url, accounts, expected)
    print(
        f"Done: {len(accounts)} accounts ready, {len(entries)} Reels verified "
        f"({uploaded} processed, {resumed} resumed)."
    )
    print(f"Local credentials: {accounts_path}")


if __name__ == "__main__":
    main()
