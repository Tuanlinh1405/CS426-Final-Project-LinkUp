package com.linkup.service

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Pure state-machine tests — no database, no server. */
class FriendshipRulesTest {

    private val alice = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val bob = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    private val carol = UUID.fromString("00000000-0000-0000-0000-0000000000c3")

    private fun pending(from: UUID, to: UUID) = StoredFriendship(from, to, FriendshipRow.PENDING)
    private fun accepted(from: UUID, to: UUID) = StoredFriendship(from, to, FriendshipRow.ACCEPTED)

    // ---- how each side sees the same row ---------------------------------

    @Test
    fun `no row means no relationship`() {
        assertEquals(FriendshipStatus.NONE, FriendshipRules.statusFor(null, alice))
    }

    @Test
    fun `a pending row reads differently from each side`() {
        val row = pending(alice, bob)
        assertEquals(FriendshipStatus.REQUEST_SENT, FriendshipRules.statusFor(row, alice))
        assertEquals(FriendshipStatus.REQUEST_RECEIVED, FriendshipRules.statusFor(row, bob))
    }

    @Test
    fun `an accepted row reads as friends from both sides`() {
        val row = accepted(alice, bob)
        assertEquals(FriendshipStatus.FRIENDS, FriendshipRules.statusFor(row, alice))
        assertEquals(FriendshipStatus.FRIENDS, FriendshipRules.statusFor(row, bob))
    }

    @Test
    fun `an unrelated viewer sees nothing`() {
        assertEquals(FriendshipStatus.NONE, FriendshipRules.statusFor(pending(alice, bob), carol))
    }

    @Test
    fun `an unrecognised stored status degrades to none`() {
        val row = StoredFriendship(alice, bob, "BLOCKED_LATER_FEATURE")
        assertEquals(FriendshipStatus.NONE, FriendshipRules.statusFor(row, alice))
    }

    // ---- sending a request -----------------------------------------------

    @Test
    fun `a first request creates a row`() {
        assertEquals(RequestOutcome.Create, FriendshipRules.requestOutcome(null, alice, bob))
    }

    @Test
    fun `you cannot friend yourself`() {
        assertIs<RequestOutcome.Rejected>(FriendshipRules.requestOutcome(null, alice, alice))
    }

    @Test
    fun `sending twice is idempotent rather than an error`() {
        assertEquals(
            RequestOutcome.AlreadyPending,
            FriendshipRules.requestOutcome(pending(alice, bob), alice, bob)
        )
    }

    @Test
    fun `requesting someone who already requested you accepts instead of duplicating`() {
        assertEquals(
            RequestOutcome.AcceptExisting,
            FriendshipRules.requestOutcome(pending(bob, alice), alice, bob)
        )
    }

    @Test
    fun `requesting an existing friend is a no-op`() {
        assertEquals(
            RequestOutcome.AlreadyFriends,
            FriendshipRules.requestOutcome(accepted(alice, bob), alice, bob)
        )
        assertEquals(
            RequestOutcome.AlreadyFriends,
            FriendshipRules.requestOutcome(accepted(bob, alice), alice, bob)
        )
    }

    // ---- responding -------------------------------------------------------

    @Test
    fun `the addressee may respond`() {
        assertEquals(ResponseOutcome.Apply, FriendshipRules.responseOutcome(pending(alice, bob), bob))
    }

    @Test
    fun `the requester may not accept their own request`() {
        assertIs<ResponseOutcome.Rejected>(
            FriendshipRules.responseOutcome(pending(alice, bob), alice)
        )
    }

    @Test
    fun `a stranger may not respond`() {
        assertIs<ResponseOutcome.Rejected>(
            FriendshipRules.responseOutcome(pending(alice, bob), carol)
        )
    }

    @Test
    fun `responding to nothing, or to an accepted row, is rejected`() {
        assertIs<ResponseOutcome.Rejected>(FriendshipRules.responseOutcome(null, bob))
        assertIs<ResponseOutcome.Rejected>(
            FriendshipRules.responseOutcome(accepted(alice, bob), bob)
        )
    }

    // ---- cancelling -------------------------------------------------------

    @Test
    fun `only the requester can cancel`() {
        assertEquals(ResponseOutcome.Apply, FriendshipRules.cancelOutcome(pending(alice, bob), alice))
        assertIs<ResponseOutcome.Rejected>(FriendshipRules.cancelOutcome(pending(alice, bob), bob))
        assertIs<ResponseOutcome.Rejected>(FriendshipRules.cancelOutcome(null, alice))
    }

    @Test
    fun `cancelling an accepted friendship points you at unfriend`() {
        val outcome = FriendshipRules.cancelOutcome(accepted(alice, bob), alice)
        assertIs<ResponseOutcome.Rejected>(outcome)
        assertEquals(true, outcome.reason.contains("remove the friend", ignoreCase = true))
    }

    // ---- unfriending ------------------------------------------------------

    @Test
    fun `unfriend needs an accepted row`() {
        assertEquals(ResponseOutcome.Apply, FriendshipRules.unfriendOutcome(accepted(alice, bob)))
        assertIs<ResponseOutcome.Rejected>(FriendshipRules.unfriendOutcome(pending(alice, bob)))
        assertIs<ResponseOutcome.Rejected>(FriendshipRules.unfriendOutcome(null))
    }
}
