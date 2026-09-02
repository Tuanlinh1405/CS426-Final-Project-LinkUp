#!/usr/bin/env bash
#
# End-to-end smoke test for the profile API.
#
# Start the backend first (./gradlew :backend:run), then run this from the repo root:
#     bash scripts/profile-api-smoke.sh
#
# It registers two throwaway users against whatever database the backend is
# pointed at, so run it against a dev database, not production. Requires curl and jq.
#
set -u
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
python3 -c "import base64,sys;open(sys.argv[1],'wb').write(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='))" "$TMP/px.png"
B=http://127.0.0.1:8080
S=$RANDOM$RANDOM
pass=0; fail=0
chk() { # name expected actual
  if [ "$2" = "$3" ]; then echo "  PASS  $1 ($3)"; pass=$((pass+1));
  else echo "  FAIL  $1 — expected $2, got $3"; fail=$((fail+1)); fi
}

reg() { curl -s -X POST $B/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1@t.local\",\"username\":\"$1\",\"password\":\"Passw0rd!\",\"fullName\":\"$2\"}"; }

echo "== setup =="
A_JSON=$(reg "alpha$S" "Alpha One");  A_TOK=$(echo "$A_JSON" | jq -r .token); A_ID=$(echo "$A_JSON" | jq -r .user.id)
B_JSON=$(reg "beta$S"  "Beta Two");   B_TOK=$(echo "$B_JSON" | jq -r .token); B_ID=$(echo "$B_JSON" | jq -r .user.id)
echo "  A=alpha$S  B=beta$S"
[ "$A_TOK" != "null" ] && echo "  tokens obtained" || { echo "  SETUP FAILED"; exit 1; }
AH="Authorization: Bearer $A_TOK"; BH="Authorization: Bearer $B_TOK"

echo "== 1. GET /profile/me =="
R=$(curl -s -w '\n%{http_code}' -H "$AH" $B/profile/me); C=$(echo "$R"|tail -1); J=$(echo "$R"|head -n -1)
chk "status" 200 "$C"
chk "isMe" true "$(echo "$J"|jq -r .isMe)"
chk "own email visible" "alpha$S@t.local" "$(echo "$J"|jq -r .email)"
chk "followerCount" 0 "$(echo "$J"|jq -r .followerCount)"

echo "== 2. unauthenticated is rejected =="
chk "no token -> 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' $B/profile/me)"

echo "== 3. PATCH valid fields =="
R=$(curl -s -w '\n%{http_code}' -X PATCH -H "$AH" -H 'Content-Type: application/json' \
  -d '{"fullName":"Alpha Uno","bio":"Android dev","phone":"+84 (912) 345-678","location":"Da Nang","website":"linkup.dev","birthdate":"2000-04-29","gender":"prefer not to say"}' \
  $B/profile/me); C=$(echo "$R"|tail -1); J=$(echo "$R"|head -n -1)
chk "status" 200 "$C"
chk "fullName" "Alpha Uno" "$(echo "$J"|jq -r .fullName)"
chk "phone normalised" "+84912345678" "$(echo "$J"|jq -r .phone)"
chk "website gets scheme" "https://linkup.dev" "$(echo "$J"|jq -r .website)"
chk "gender normalised" "PREFER_NOT_TO_SAY" "$(echo "$J"|jq -r .gender)"
chk "birthdate" "2000-04-29" "$(echo "$J"|jq -r .birthdate)"

echo "== 4. persistence =="
chk "bio persisted" "Android dev" "$(curl -s -H "$AH" $B/profile/me | jq -r .bio)"
chk "location persisted" "Da Nang" "$(curl -s -H "$AH" $B/profile/me | jq -r .location)"

echo "== 5. validation rejects bad input =="
R=$(curl -s -w '\n%{http_code}' -X PATCH -H "$AH" -H 'Content-Type: application/json' \
  -d '{"phone":"call me maybe"}' $B/profile/me); C=$(echo "$R"|tail -1); J=$(echo "$R"|head -n -1)
chk "bad phone -> 422" 422 "$C"
chk "field error keyed to phone" "true" "$(echo "$J"|jq -r 'has("fieldErrors") and (.fieldErrors|has("phone"))')"
chk "bad email -> 422" 422 "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "$AH" -H 'Content-Type: application/json' -d '{"email":"nope"}' $B/profile/me)"
chk "future birthdate -> 422" 422 "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "$AH" -H 'Content-Type: application/json' -d '{"birthdate":"2099-01-01"}' $B/profile/me)"
chk "under-13 -> 422" 422 "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "$AH" -H 'Content-Type: application/json' -d '{"birthdate":"2020-01-01"}' $B/profile/me)"

echo "== 6. uniqueness conflict =="
R=$(curl -s -w '\n%{http_code}' -X PATCH -H "$AH" -H 'Content-Type: application/json' \
  -d "{\"username\":\"beta$S\"}" $B/profile/me); C=$(echo "$R"|tail -1); J=$(echo "$R"|head -n -1)
chk "taken username -> 409" 409 "$C"
chk "conflict names the field" "true" "$(echo "$J"|jq -r '.fieldErrors|has("username")')"
chk "email conflict -> 409" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "$AH" -H 'Content-Type: application/json' -d "{\"email\":\"beta$S@t.local\"}" $B/profile/me)"
chk "username unchanged after conflict" "alpha$S" "$(curl -s -H "$AH" $B/profile/me | jq -r .username)"

echo "== 7. avatar upload =="
R=$(curl -s -w '\n%{http_code}' -X POST -H "$AH" -F "file=@$TMP/px.png;type=image/png" $B/profile/me/avatar)
C=$(echo "$R"|tail -1); J=$(echo "$R"|head -n -1)
chk "status" 200 "$C"
URL=$(echo "$J"|jq -r .url)
chk "url points at /media" "true" "$(echo "$URL" | grep -q '/media/avatars/' && echo true || echo false)"
LOCAL=$(echo "$URL" | sed 's|http://10.0.2.2:8080|http://127.0.0.1:8080|')
chk "uploaded file is served" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$LOCAL")"
chk "profile now has avatarUrl" "$URL" "$(curl -s -H "$AH" $B/profile/me | jq -r .avatarUrl)"

echo "== 8. cover upload + rejections =="
chk "cover upload" 200 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AH" -F "file=@$TMP/px.png;type=image/png" $B/profile/me/cover)"
echo "not an image" > "$TMP/bad.txt"
chk "text file -> 415" 415 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AH" -F "file=@$TMP/bad.txt;type=text/plain" $B/profile/me/avatar)"
chk "no attachment -> 400" 400 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AH" -F "note=hello" $B/profile/me/avatar)"

echo "== 9. viewing someone else =="
R=$(curl -s -w '\n%{http_code}' -H "$BH" "$B/profile/alpha$S"); C=$(echo "$R"|tail -1); J=$(echo "$R"|head -n -1)
chk "lookup by username" 200 "$C"
chk "isMe false" false "$(echo "$J"|jq -r .isMe)"
chk "email hidden from others" null "$(echo "$J"|jq -r .email)"
chk "phone hidden from others" null "$(echo "$J"|jq -r .phone)"
chk "bio still public" "Android dev" "$(echo "$J"|jq -r .bio)"
chk "lookup by id" 200 "$(curl -s -o /dev/null -w '%{http_code}' -H "$BH" $B/profile/$A_ID)"
chk "unknown user -> 404" 404 "$(curl -s -o /dev/null -w '%{http_code}' -H "$BH" $B/profile/ghost-nobody-here)"

echo "== 10. follow / unfollow =="
R=$(curl -s -X POST -H "$BH" $B/profile/$A_ID/follow)
chk "follow sets state" true "$(echo "$R"|jq -r .isFollowing)"
chk "follower count" 1 "$(echo "$R"|jq -r .followerCount)"
chk "A sees isFollowing from B" true "$(curl -s -H "$BH" $B/profile/$A_ID | jq -r .isFollowing)"
chk "B following count" 1 "$(curl -s -H "$BH" $B/profile/me | jq -r .followingCount)"
chk "follow is idempotent" 1 "$(curl -s -X POST -H "$BH" $B/profile/$A_ID/follow | jq -r .followerCount)"
chk "cannot follow self" 400 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AH" $B/profile/$A_ID/follow)"
R=$(curl -s -X DELETE -H "$BH" $B/profile/$A_ID/follow)
chk "unfollow clears state" false "$(echo "$R"|jq -r .isFollowing)"
chk "follower count back to 0" 0 "$(echo "$R"|jq -r .followerCount)"

echo "== 11. remove photos =="
R=$(curl -s -X DELETE -H "$AH" $B/profile/me/avatar)
chk "avatar cleared" null "$(echo "$R"|jq -r .avatarUrl)"
chk "old avatar file deleted" 404 "$(curl -s -o /dev/null -w '%{http_code}' "$LOCAL")"
chk "cover cleared" null "$(curl -s -X DELETE -H "$AH" $B/profile/me/cover | jq -r .coverUrl)"

echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
