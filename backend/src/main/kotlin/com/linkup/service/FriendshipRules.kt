package com.linkup.service

import java.util.UUID

/** Row states persisted in `friendships.status`. */
object FriendshipRow {
    const val PENDING = "PENDING"
    const val ACCEPTED = "ACCEPTED"
}

/** How a friendship looks from one particular person's side. */
enum class FriendshipStatus {
    /** No row at all — the button reads "Add friend". */
    NONE,

    /** The viewer asked and is waiting — "Requested", cancellable. */
    REQUEST_SENT,

    /** The other person asked — "Respond", with accept and decline. */
    REQUEST_RECEIVED,

    FRIENDS
}

/** The stored row, reduced to what the rules need. */
data class StoredFriendship(
    val requesterId: UUID,
    val addresseeId: UUID,
    val status: String
)

/** What sending a request should do, given whatever row already exists. */
sealed interface RequestOutcome {
    /** No row yet: insert a PENDING one. */
    data object Create : RequestOutcome

    /**
     * The other person had already asked, so asking back is consent.
     * Promote their row to ACCEPTED instead of creating a second one.
     */
    data object AcceptExisting : RequestOutcome

    /** Already waiting on this exact request; do nothing and stay idempotent. */
    data object AlreadyPending : RequestOutcome

    data object AlreadyFriends : RequestOutcome

    data class Rejected(val reason: String) : RequestOutcome
}

/** What responding to a request should do. */
sealed interface ResponseOutcome {
    data object Apply : ResponseOutcome

    data class Rejected(val reason: String) : ResponseOutcome
}

/**
 * The friendship state machine, free of Exposed and Ktor so it can be unit tested.
 *
 * Every transition is decided here and merely applied by the repository, which keeps
 * the awkward cases — asking someone who already asked you, asking twice, responding
 * to a request that is not yours — in one readable place.
 */
object FriendshipRules {

    /** Reduces a stored row to what [viewerId] should see. */
    fun statusFor(row: StoredFriendship?, viewerId: UUID): FriendshipStatus {
        if (row == null) return FriendshipStatus.NONE
        return when {
            row.status == FriendshipRow.ACCEPTED -> FriendshipStatus.FRIENDS
            row.status != FriendshipRow.PENDING -> FriendshipStatus.NONE
            row.requesterId == viewerId -> FriendshipStatus.REQUEST_SENT
            row.addresseeId == viewerId -> FriendshipStatus.REQUEST_RECEIVED
            else -> FriendshipStatus.NONE
        }
    }

    fun requestOutcome(
        existing: StoredFriendship?,
        requesterId: UUID,
        addresseeId: UUID
    ): RequestOutcome {
        if (requesterId == addresseeId) {
            return RequestOutcome.Rejected("You can't send yourself a friend request")
        }
        if (existing == null) return RequestOutcome.Create
        if (existing.status == FriendshipRow.ACCEPTED) return RequestOutcome.AlreadyFriends

        return if (existing.requesterId == requesterId) {
            RequestOutcome.AlreadyPending
        } else {
            // They asked first; this request is the acceptance.
            RequestOutcome.AcceptExisting
        }
    }

    /** [responderId] accepting or declining a request must be its addressee. */
    fun responseOutcome(existing: StoredFriendship?, responderId: UUID): ResponseOutcome {
        if (existing == null) return ResponseOutcome.Rejected("There's no pending request")
        if (existing.status == FriendshipRow.ACCEPTED) {
            return ResponseOutcome.Rejected("You're already friends")
        }
        if (existing.addresseeId != responderId) {
            return ResponseOutcome.Rejected("You can only respond to requests sent to you")
        }
        return ResponseOutcome.Apply
    }

    /** Cancelling is the requester withdrawing their own pending request. */
    fun cancelOutcome(existing: StoredFriendship?, cancellerId: UUID): ResponseOutcome {
        if (existing == null) return ResponseOutcome.Rejected("There's no pending request")
        if (existing.status == FriendshipRow.ACCEPTED) {
            return ResponseOutcome.Rejected("You're already friends — remove the friend instead")
        }
        if (existing.requesterId != cancellerId) {
            return ResponseOutcome.Rejected("You can only cancel a request you sent")
        }
        return ResponseOutcome.Apply
    }

    fun unfriendOutcome(existing: StoredFriendship?): ResponseOutcome {
        if (existing == null || existing.status != FriendshipRow.ACCEPTED) {
            return ResponseOutcome.Rejected("You're not friends with this person")
        }
        return ResponseOutcome.Apply
    }
}
