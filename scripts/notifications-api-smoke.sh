#!/usr/bin/env bash
#
# End-to-end smoke test for the notifications API.
#
# Start the backend first (./gradlew :backend:run), then run this from the repo root:
#     bash scripts/notifications-api-smoke.sh
#
# It registers throwaway users and follows between them, so run it against a dev
# database, not production. Requires curl and jq. Takes a couple of minutes because
# every step is a real round trip to the database.
#
set -u
B=http://127.0.0.1:8080
S=$RANDOM$RANDOM
pass=0; fail=0
chk() { if [ "$2" = "$3" ]; then echo "  PASS  $1 ($3)"; pass=$((pass+1));
        else echo "  FAIL  $1 — expected $2, got $3"; fail=$((fail+1)); fi }
reg() { curl -s -X POST $B/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1@t.local\",\"username\":\"$1\",\"password\":\"Passw0rd!\",\"fullName\":\"$2\"}"; }

A=$(reg "na$S" "Alpha One"); ATOK=$(echo "$A"|jq -r .token); AID=$(echo "$A"|jq -r .user.id)
Bj=$(reg "nb$S" "Beta Two"); BTOK=$(echo "$Bj"|jq -r .token); BID=$(echo "$Bj"|jq -r .user.id)
AH="Authorization: Bearer $ATOK"; BH="Authorization: Bearer $BTOK"

echo "== 1. new account gets a welcome notification =="
R=$(curl -s -H "$AH" "$B/notifications")
chk "one item" 1 "$(echo "$R"|jq '.items|length')"
chk "type SYSTEM" SYSTEM "$(echo "$R"|jq -r .items[0].type)"
chk "unreadCount" 1 "$(echo "$R"|jq -r .unreadCount)"
chk "nextCursor null on last page" null "$(echo "$R"|jq -r .nextCursor)"
chk "actor is the user themselves" "na$S" "$(echo "$R"|jq -r .items[0].actor.username)"

echo "== 2. unauthenticated =="
chk "no token -> 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' $B/notifications)"

echo "== 3. following produces a notification =="
curl -s -X POST -H "$BH" $B/profile/$AID/follow > /dev/null
R=$(curl -s -H "$AH" "$B/notifications")
chk "now two items" 2 "$(echo "$R"|jq '.items|length')"
chk "newest is FOLLOW" FOLLOW "$(echo "$R"|jq -r .items[0].type)"
chk "actor is B" "nb$S" "$(echo "$R"|jq -r .items[0].actor.username)"
chk "actor full name carried" "Beta Two" "$(echo "$R"|jq -r .items[0].actor.fullName)"
chk "targetId is B (opens their profile)" "$BID" "$(echo "$R"|jq -r .items[0].targetId)"
chk "unread count 2" 2 "$(echo "$R"|jq -r .unreadCount)"
chk "unread-count endpoint agrees" 2 "$(curl -s -H "$AH" $B/notifications/unread-count | jq -r .unreadCount)"
chk "B got nothing from their own action" 1 "$(curl -s -H "$BH" $B/notifications | jq '.items|length')"

echo "== 4. unfollow withdraws it, refollow does not duplicate =="
curl -s -X DELETE -H "$BH" $B/profile/$AID/follow > /dev/null
chk "back to one item" 1 "$(curl -s -H "$AH" $B/notifications | jq '.items|length')"
for i in 1 2 3; do
  curl -s -X POST -H "$BH" $B/profile/$AID/follow > /dev/null
  curl -s -X DELETE -H "$BH" $B/profile/$AID/follow > /dev/null
done
curl -s -X POST -H "$BH" $B/profile/$AID/follow > /dev/null
chk "toggling 4x leaves exactly one FOLLOW" 1 "$(curl -s -H "$AH" $B/notifications | jq '[.items[]|select(.type=="FOLLOW")]|length')"

echo "== 5. read state =="
NID=$(curl -s -H "$AH" $B/notifications | jq -r .items[0].id)
chk "mark one read" 1 "$(curl -s -X PUT -H "$AH" $B/notifications/$NID/read | jq -r .unreadCount)"
chk "row reflects it" true "$(curl -s -H "$AH" $B/notifications | jq -r '.items[]|select(.id=="'$NID'")|.isRead')"
chk "undo with read=false" 2 "$(curl -s -X PUT -H "$AH" "$B/notifications/$NID/read?read=false" | jq -r .unreadCount)"
chk "mark all read" 0 "$(curl -s -X PUT -H "$AH" $B/notifications/read-all | jq -r .unreadCount)"
chk "unread filter now empty" 0 "$(curl -s -H "$AH" "$B/notifications?filter=unread" | jq '.items|length')"
chk "all filter still full" 2 "$(curl -s -H "$AH" "$B/notifications" | jq '.items|length')"

echo "== 6. one user cannot touch another's =="
chk "B reading A's id -> 404" 404 "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H "$BH" $B/notifications/$NID/read)"
chk "B deleting A's id -> 404" 404 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "$BH" $B/notifications/$NID)"
chk "A's notification survived" 2 "$(curl -s -H "$AH" $B/notifications | jq '.items|length')"
chk "malformed id -> 400" 400 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "$AH" $B/notifications/not-a-uuid)"

echo "== 7. cursor pagination =="
for i in 1 2 3 4 5 6; do
  T=$(reg "nf$S$i" "Follower $i" | jq -r .token)
  curl -s -X POST -H "Authorization: Bearer $T" $B/profile/$AID/follow > /dev/null
done
TOTAL=$(curl -s -H "$AH" "$B/notifications?limit=50" | jq '.items|length')
chk "8 notifications total" 8 "$TOTAL"
CUR=""; SEEN=""; PAGES=0
while : ; do
  URL="$B/notifications?limit=3"; [ -n "$CUR" ] && URL="$URL&cursor=$CUR"
  P=$(curl -s -H "$AH" "$URL")
  SEEN="$SEEN $(echo "$P"|jq -r '.items[].id')"
  PAGES=$((PAGES+1))
  CUR=$(echo "$P"|jq -r '.nextCursor // empty')
  [ -z "$CUR" ] && break
  [ "$PAGES" -gt 10 ] && break
done
UNIQ=$(echo $SEEN | tr ' ' '\n' | grep -c . )
DEDUP=$(echo $SEEN | tr ' ' '\n' | grep . | sort -u | wc -l)
chk "walked 3 pages" 3 "$PAGES"
chk "collected every row" 8 "$UNIQ"
chk "no duplicates across pages" "$UNIQ" "$DEDUP"
chk "newest first" FOLLOW "$(curl -s -H "$AH" "$B/notifications?limit=1" | jq -r .items[0].type)"
chk "garbage cursor starts from the top" 3 "$(curl -s -H "$AH" "$B/notifications?limit=3&cursor=nonsense" | jq '.items|length')"
chk "limit is capped at 50" 8 "$(curl -s -H "$AH" "$B/notifications?limit=9999" | jq '.items|length')"

echo "== 8. delete =="
DID=$(curl -s -H "$AH" $B/notifications | jq -r .items[0].id)
chk "delete one" 7 "$(curl -s -X DELETE -H "$AH" $B/notifications/$DID > /dev/null; curl -s -H "$AH" "$B/notifications?limit=50" | jq '.items|length')"
chk "clear all" 7 "$(curl -s -X DELETE -H "$AH" $B/notifications | jq -r .affected)"
chk "list is empty" 0 "$(curl -s -H "$AH" $B/notifications | jq '.items|length')"
chk "B's own notifications untouched" 1 "$(curl -s -H "$BH" $B/notifications | jq '.items|length')"

echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
