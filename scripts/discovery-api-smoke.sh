#!/usr/bin/env bash
#
# End-to-end smoke test for people discovery: user search, follower and following
# lists, and the "me" alias.
#
# Start the backend first (./gradlew :backend:run), then run from the repo root:
#     bash scripts/discovery-api-smoke.sh
#
# Registers throwaway users, so point it at a dev database. Requires curl and jq.
#
set -u
B=http://127.0.0.1:8080
S=$RANDOM$RANDOM
pass=0; fail=0
chk() { if [ "$2" = "$3" ]; then echo "  PASS  $1 ($3)"; pass=$((pass+1));
        else echo "  FAIL  $1 — expected $2, got $3"; fail=$((fail+1)); fi }
reg() { curl -s -X POST $B/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1@t.local\",\"username\":\"$1\",\"password\":\"Passw0rd!\",\"fullName\":\"$2\"}"; }

A=$(reg "zed${S}a" "Zed Alpha $S");  ATOK=$(echo "$A"|jq -r .token); AID=$(echo "$A"|jq -r .user.id)
Bj=$(reg "zed${S}b" "Zed Bravo $S"); BTOK=$(echo "$Bj"|jq -r .token); BID=$(echo "$Bj"|jq -r .user.id)
C=$(reg "zed${S}c" "Zed Charlie $S"); CTOK=$(echo "$C"|jq -r .token); CID=$(echo "$C"|jq -r .user.id)
AH="Authorization: Bearer $ATOK"; BH="Authorization: Bearer $BTOK"; CH="Authorization: Bearer $CTOK"

echo "== 1. user search =="
chk "unauthenticated -> 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' "$B/users/search?q=zed")"
R=$(curl -s -H "$AH" "$B/users/search?q=zed$S")
chk "finds all three by username prefix" 3 "$(echo "$R"|jq '.items|length')"
chk "total reported" 3 "$(echo "$R"|jq -r .total)"
chk "sorted by username" "zed${S}a" "$(echo "$R"|jq -r .items[0].username)"
chk "marks yourself" true "$(echo "$R"|jq -r '.items[]|select(.username=="zed'$S'a")|.isMe')"
chk "others are not you" false "$(echo "$R"|jq -r '.items[]|select(.username=="zed'$S'b")|.isMe')"
chk "not following anyone yet" false "$(echo "$R"|jq -r '.items[]|select(.username=="zed'$S'b")|.isFollowing')"

echo "== 2. search matches full name too, case-insensitively =="
chk "by full name" 1 "$(curl -s -H "$AH" "$B/users/search?q=Zed%20Bravo%20$S" | jq '.items|length')"
chk "lowercase query" 1 "$(curl -s -H "$AH" "$B/users/search?q=zed%20bravo%20$S" | jq '.items|length')"
chk "uppercase query" 1 "$(curl -s -H "$AH" "$B/users/search?q=ZED%20BRAVO%20$S" | jq '.items|length')"
chk "blank query returns nothing" 0 "$(curl -s -H "$AH" "$B/users/search?q=" | jq '.items|length')"
chk "no match returns empty" 0 "$(curl -s -H "$AH" "$B/users/search?q=qqzzxx$S" | jq '.items|length')"
chk "wildcard is escaped, not interpreted" 0 "$(curl -s -H "$AH" "$B/users/search?q=%25" | jq '.items|length')"

echo "== 3. follow state shows in search =="
curl -s -X POST -H "$AH" $B/profile/$BID/follow > /dev/null
chk "isFollowing flips" true "$(curl -s -H "$AH" "$B/users/search?q=zed$S" | jq -r '.items[]|select(.username=="zed'$S'b")|.isFollowing')"
chk "and not for the third party" false "$(curl -s -H "$AH" "$B/users/search?q=zed$S" | jq -r '.items[]|select(.username=="zed'$S'c")|.isFollowing')"

echo "== 4. followers / following lists =="
curl -s -X POST -H "$CH" $B/profile/$BID/follow > /dev/null
R=$(curl -s -H "$AH" $B/profile/$BID/followers)
chk "B has two followers" 2 "$(echo "$R"|jq '.items|length')"
chk "A is among them" 1 "$(echo "$R"|jq '[.items[]|select(.username=="zed'$S'a")]|length')"
chk "viewer sees themselves flagged" true "$(echo "$R"|jq -r '.items[]|select(.username=="zed'$S'a")|.isMe')"
chk "A following list has B" 1 "$(curl -s -H "$AH" $B/profile/$AID/following | jq '.items|length')"
chk "B follows nobody" 0 "$(curl -s -H "$BH" $B/profile/$BID/following | jq '.items|length')"
chk "lists work by username too" 2 "$(curl -s -H "$AH" $B/profile/zed${S}b/followers | jq '.items|length')"
chk "unknown user -> 404" 404 "$(curl -s -o /dev/null -w '%{http_code}' -H "$AH" $B/profile/nobody$S/followers)"
chk "counts agree with the profile" 2 "$(curl -s -H "$AH" $B/profile/$BID | jq -r .followerCount)"

echo "== 5. cross-check: C sees its own follow state in B's follower list =="
chk "C sees itself flagged isMe" true "$(curl -s -H "$CH" $B/profile/$BID/followers | jq -r '.items[]|select(.username=="zed'$S'c")|.isMe')"
chk "C not following A" false "$(curl -s -H "$CH" $B/profile/$BID/followers | jq -r '.items[]|select(.username=="zed'$S'a")|.isFollowing')"

echo "== 6. pagination of people lists =="
R=$(curl -s -H "$AH" "$B/profile/$BID/followers?limit=1")
chk "one per page" 1 "$(echo "$R"|jq '.items|length')"
CUR=$(echo "$R"|jq -r .nextCursor)
chk "cursor offered" "true" "$([ "$CUR" != "null" ] && echo true || echo false)"
chk "second page has the other" 1 "$(curl -s -H "$AH" "$B/profile/$BID/followers?limit=1&cursor=$CUR" | jq '.items|length')"
FIRST=$(echo "$R"|jq -r .items[0].username)
SECOND=$(curl -s -H "$AH" "$B/profile/$BID/followers?limit=1&cursor=$CUR" | jq -r .items[0].username)
chk "pages do not repeat" "true" "$([ "$FIRST" != "$SECOND" ] && echo true || echo false)"
chk "search limit capped" 3 "$(curl -s -H "$AH" "$B/users/search?q=zed$S&limit=9999" | jq '.items|length')"

echo "== 7. unfollow updates every view =="
curl -s -X DELETE -H "$AH" $B/profile/$BID/follow > /dev/null
chk "search shows not following" false "$(curl -s -H "$AH" "$B/users/search?q=zed$S" | jq -r '.items[]|select(.username=="zed'$S'b")|.isFollowing')"
chk "follower list shrank" 1 "$(curl -s -H "$AH" $B/profile/$BID/followers | jq '.items|length')"
chk "A follows nobody now" 0 "$(curl -s -H "$AH" $B/profile/$AID/following | jq '.items|length')"

echo "== 8. the 'me' alias resolves to the caller =="
chk "GET /profile/me/followers" 200 "$(curl -s -o /dev/null -w '%{http_code}' -H "$CH" $B/profile/me/followers)"
chk "GET /profile/me/following" 200 "$(curl -s -o /dev/null -w '%{http_code}' -H "$CH" $B/profile/me/following)"
chk "C follows exactly one person" 1 "$(curl -s -H "$CH" $B/profile/me/following | jq '.items|length')"
chk "and it is B" "zed${S}b" "$(curl -s -H "$CH" $B/profile/me/following | jq -r .items[0].username)"
chk "me matches the explicit id" "$(curl -s -H "$CH" $B/profile/$CID/following | jq -r .total)" "$(curl -s -H "$CH" $B/profile/me/following | jq -r .total)"

echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
