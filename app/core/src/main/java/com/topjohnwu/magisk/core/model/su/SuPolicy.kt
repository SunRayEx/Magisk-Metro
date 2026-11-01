package com.topjohnwu.magisk.core.model.su

import com.topjohnwu.magisk.core.data.magiskdb.MagiskDB

class SuPolicy(
    var uid: Int,
    var policy: Int = DENY,
    var logging: Boolean = true,
    var notification: Boolean = true,
    var remain: Long = 0L,
    // Loaded policies retain their absolute deadline when flags or policy values are edited.
    var expiresAt: Long? = null,
) {
    companion object {
        const val QUERY = 0
        const val DENY = 1
        const val ALLOW = 2
        const val RESTRICT = 3
        const val ZERO = 4
    }

    fun toMap(): MutableMap<String, Any> {
        val until = expiresAt ?: if (remain <= 0) remain else
            MagiskDB.Literal("(strftime(\"%s\", \"now\") + $remain)")
        return mutableMapOf(
            "uid" to uid,
            "policy" to policy,
            "until" to until,
            "logging" to logging,
            "notification" to notification
        )
    }
}
