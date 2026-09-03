package com.example.linkup.data.model

enum class MessageStatus {
    SENT,
    DELIVERED,
    SEEN;

    companion object {
        fun fromString(status: String?): MessageStatus {
            return when (status?.uppercase()) {
                "DELIVERED" -> DELIVERED
                "SEEN" -> SEEN
                else -> SENT
            }
        }
    }
}
