#!/usr/bin/env bash
#
# End-to-end smoke test for friends: requests, responses, lists, mutual friends,
# suggestions, and the notifications each action produces.
#
# Start the backend first (./gradlew :backend:run), then run from the repo root:
#     bash scripts/friends-api-smoke.sh
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

A=$(reg "fa$S" "Amy $S");    ATOK=$(echo "$A"|jq -r .token);  AID=$(echo "$A"|jq -r .user.id)
Bj=$(reg "fb$S" "Ben $S");   BTOK=$(echo "$Bj"|jq -r .token); BID=$(echo "$Bj"|jq -r .user.id)
C=$(reg "fc$S" "Cara $S");   CTOK=$(echo "$C"|jq -r .token);  CID=$(echo "$C"|jq -r .user.id)
D=$(reg "fd$S" "Dan $S");    DTOK=$(echo "$D"|jq -r .token);  DID=$(echo "$D"|jq -r .user.id)
AH="Authorization: Bearer $ATOK"; BH="Authorization: Bearer $BTOK"
CH="Authorization: Bearer $CTOK"; DH="Authorization: Bearer $DTOK"

echo "== 1. sending a request =="
chk "starts at NONE" NONE "$(curl -s -H "$AH" $B/friends/$BID/state | jq -r .status)"
R=$(curl -s -X POST -H "$AH" $B/friends/$BID/request)
chk "sender sees REQUEST_SENT" REQUEST_SENT "$(echo "$R"|jq -r .status)"
chk "receiver sees REQUEST_RECEIVED" REQUEST_RECEIVED "$(curl -s -H "$BH" $B/friends/$AID/state | jq -r .status)"
chk "receiver request badge is 1" 1 "$(curl -s -H "$BH" $B/friends/requests/count | jq -r .unreadCount)"
chk "receiver got a FRIEND_REQUEST notification" 1 "$(curl -s -H "$BH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_REQUEST")]|length')"
chk "notification points at the requester" "$AID" "$(curl -s -H "$BH" $B/notifications | jq -r '.items[]|select(.type=="FRIEND_REQUEST")|.targetId')"
chk "sender has no new notification" 0 "$(curl -s -H "$AH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_REQUEST")]|length')"

echo "== 2. requests are idempotent and self-requests rejected =="
chk "re-sending stays REQUEST_SENT" REQUEST_SENT "$(curl -s -X POST -H "$AH" $B/friends/$BID/request | jq -r .status)"
chk "still exactly one notification" 1 "$(curl -s -H "$BH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_REQUEST")]|length')"
chk "cannot friend yourself" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AH" $B/friends/$AID/request)"
chk "unauthenticated -> 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/friends/$BID/request)"

echo "== 3. only the addressee can respond =="
chk "requester cannot accept their own" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H "$AH" $B/friends/$BID/accept)"
chk "a stranger cannot accept" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H "$CH" $B/friends/$AID/accept)"
chk "addressee cannot cancel" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "$BH" $B/friends/$AID/request)"

echo "== 4. cancelling withdraws request and notification =="
chk "cancel returns NONE" NONE "$(curl -s -X DELETE -H "$AH" $B/friends/$BID/request | jq -r .status)"
chk "receiver back to NONE" NONE "$(curl -s -H "$BH" $B/friends/$AID/state | jq -r .status)"
chk "notification withdrawn" 0 "$(curl -s -H "$BH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_REQUEST")]|length')"
chk "badge back to 0" 0 "$(curl -s -H "$BH" $B/friends/requests/count | jq -r .unreadCount)"

echo "== 5. accepting =="
curl -s -X POST -H "$AH" $B/friends/$BID/request > /dev/null
R=$(curl -s -X PUT -H "$BH" $B/friends/$AID/accept)
chk "accepter sees FRIENDS" FRIENDS "$(echo "$R"|jq -r .status)"
chk "requester sees FRIENDS" FRIENDS "$(curl -s -H "$AH" $B/friends/$BID/state | jq -r .status)"
chk "friend count is 1" 1 "$(echo "$R"|jq -r .friendCount)"
chk "requester got FRIEND_ACCEPT" 1 "$(curl -s -H "$AH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_ACCEPT")]|length')"
chk "accepter's request notification cleared" 0 "$(curl -s -H "$BH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_REQUEST")]|length')"
chk "badge cleared" 0 "$(curl -s -H "$BH" $B/friends/requests/count | jq -r .unreadCount)"
chk "requesting an existing friend is a no-op" FRIENDS "$(curl -s -X POST -H "$AH" $B/friends/$BID/request | jq -r .status)"

echo "== 6. friend lists =="
chk "A has one friend" 1 "$(curl -s -H "$AH" $B/friends | jq '.items|length')"
chk "and it is Ben" "fb$S" "$(curl -s -H "$AH" $B/friends | jq -r .items[0].username)"
chk "row carries FRIENDS status" FRIENDS "$(curl -s -H "$AH" $B/friends | jq -r .items[0].friendshipStatus)"
chk "B's list has Amy" "fa$S" "$(curl -s -H "$BH" $B/friends | jq -r .items[0].username)"
chk "C has no friends" 0 "$(curl -s -H "$CH" $B/friends | jq '.items|length')"
chk "can view someone else's friends" 1 "$(curl -s -H "$CH" "$B/friends?of=$AID" | jq '.items|length')"

echo "== 7. reciprocal request auto-accepts =="
curl -s -X POST -H "$CH" $B/friends/$DID/request > /dev/null
R=$(curl -s -X POST -H "$DH" $B/friends/$CID/request)
chk "asking back becomes friendship" FRIENDS "$(echo "$R"|jq -r .status)"
chk "no duplicate row: C also FRIENDS" FRIENDS "$(curl -s -H "$CH" $B/friends/$DID/state | jq -r .status)"
chk "C has exactly one friend" 1 "$(curl -s -H "$CH" $B/friends | jq '.items|length')"
chk "original requester notified" 1 "$(curl -s -H "$CH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_ACCEPT")]|length')"
chk "C's pending request notification gone" 0 "$(curl -s -H "$DH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_REQUEST")]|length')"

echo "== 8. incoming / outgoing lists =="
curl -s -X POST -H "$AH" $B/friends/$CID/request > /dev/null
chk "A outgoing has Cara" 1 "$(curl -s -H "$AH" $B/friends/requests/outgoing | jq '.items|length')"
chk "A incoming empty" 0 "$(curl -s -H "$AH" $B/friends/requests/incoming | jq '.items|length')"
chk "C incoming has Amy" 1 "$(curl -s -H "$CH" $B/friends/requests/incoming | jq '.items|length')"
chk "C incoming row status" REQUEST_RECEIVED "$(curl -s -H "$CH" $B/friends/requests/incoming | jq -r .items[0].friendshipStatus)"
chk "C outgoing empty" 0 "$(curl -s -H "$CH" $B/friends/requests/outgoing | jq '.items|length')"

echo "== 9. declining =="
chk "decline returns NONE" NONE "$(curl -s -X PUT -H "$CH" $B/friends/$AID/decline | jq -r .status)"
chk "request notification cleared" 0 "$(curl -s -H "$CH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_REQUEST")]|length')"
chk "A outgoing empty again" 0 "$(curl -s -H "$AH" $B/friends/requests/outgoing | jq '.items|length')"
chk "can re-request after a decline" REQUEST_SENT "$(curl -s -X POST -H "$AH" $B/friends/$CID/request | jq -r .status)"
curl -s -X DELETE -H "$AH" $B/friends/$CID/request > /dev/null

echo "== 10. mutual friends =="
curl -s -X POST -H "$CH" $B/friends/$AID/request > /dev/null
curl -s -X PUT -H "$AH" $B/friends/$CID/accept > /dev/null
chk "B and C share one mutual (Amy)" 1 "$(curl -s -H "$BH" $B/friends/$CID/state | jq -r .mutualFriendCount)"
chk "mutual count is symmetric" 1 "$(curl -s -H "$CH" $B/friends/$BID/state | jq -r .mutualFriendCount)"
chk "profile exposes it too" 1 "$(curl -s -H "$BH" $B/profile/$CID | jq -r .mutualFriendCount)"
chk "no mutuals with a stranger" 0 "$(curl -s -H "$BH" $B/friends/$DID/state | jq -r .mutualFriendCount)"

echo "== 11. profile carries friendship context =="
chk "friendshipStatus on profile" FRIENDS "$(curl -s -H "$AH" $B/profile/$BID | jq -r .friendshipStatus)"
chk "friendCount on profile" 2 "$(curl -s -H "$AH" $B/profile/$AID | jq -r .friendCount)"
chk "own profile has no self status" NONE "$(curl -s -H "$AH" $B/profile/me | jq -r .friendshipStatus)"
curl -s -X POST -H "$DH" $B/profile/$AID/follow > /dev/null
chk "isFollowedBy reflects their follow" true "$(curl -s -H "$AH" $B/profile/$DID | jq -r .isFollowedBy)"
chk "isFollowing still independent" false "$(curl -s -H "$AH" $B/profile/$DID | jq -r .isFollowing)"

echo "== 12. suggestions (friends of friends) =="
R=$(curl -s -H "$BH" $B/friends/suggestions)
chk "B is suggested Cara via Amy" 1 "$(echo "$R"|jq '[.items[]|select(.username=="fc'$S'")]|length')"
chk "suggestion shows the mutual count" 1 "$(echo "$R"|jq -r '.items[]|select(.username=="fc'$S'")|.mutualFriendCount')"
chk "existing friends are excluded" 0 "$(echo "$R"|jq '[.items[]|select(.username=="fa'$S'")]|length')"
chk "you are never suggested to yourself" 0 "$(echo "$R"|jq '[.items[]|select(.username=="fb'$S'")]|length')"
chk "a friendless account still gets suggestions" "true" "$([ "$(curl -s -H "$DH" $B/friends/suggestions | jq '.items|length')" -gt 0 ] && echo true || echo false)"

echo "== 13. unfriending =="
R=$(curl -s -X DELETE -H "$AH" $B/friends/$BID)
chk "unfriend returns NONE" NONE "$(echo "$R"|jq -r .status)"
chk "other side also NONE" NONE "$(curl -s -H "$BH" $B/friends/$AID/state | jq -r .status)"
chk "A friend count drops to 1" 1 "$(echo "$R"|jq -r .friendCount)"
chk "friend notifications cleared" 0 "$(curl -s -H "$AH" $B/notifications | jq '[.items[]|select(.type=="FRIEND_ACCEPT" and .actor.username=="fb'$S'")]|length')"
chk "unfriending a non-friend -> 409" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "$AH" $B/friends/$DID)"
chk "B's friend list is empty" 0 "$(curl -s -H "$BH" $B/friends | jq '.items|length')"

echo "== 14. unknown targets =="
chk "unknown user -> 404" 404 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AH" $B/friends/ghost$S/request)"
chk "state by username works" FRIENDS "$(curl -s -H "$AH" $B/friends/fc$S/state | jq -r .status)"

echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
