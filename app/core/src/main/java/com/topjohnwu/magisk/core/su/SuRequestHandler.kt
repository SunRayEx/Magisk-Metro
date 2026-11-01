package com.topjohnwu.magisk.core.su

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.system.Os
import android.system.OsConstants
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.data.magiskdb.PolicyDao
import com.topjohnwu.magisk.core.ktx.getPackageInfo
import com.topjohnwu.magisk.core.model.su.SuPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class SuRequestHandler(
    val pm: PackageManager,
    private val policyDB: PolicyDao
) {

    private lateinit var output: File
    private lateinit var policy: SuPolicy
    lateinit var pkgInfo: PackageInfo
        private set

    // Return true to indicate undetermined policy, require user interaction
    suspend fun start(intent: Intent): Boolean {
        if (!init(intent))
            return false

        when (Config.suAutoResponse) {
            Config.Value.SU_AUTO_DENY -> {
                respond(SuPolicy.DENY, 0)
                return false
            }
            Config.Value.SU_AUTO_ALLOW -> {
                // Automatic responses must not bypass the configured credential check.
                if (!Config.suAuth) {
                    respond(SuPolicy.ALLOW, 0)
                    return false
                }
            }
        }

        return true
    }

    private suspend fun init(intent: Intent): Boolean {
        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val fifo = intent.getStringExtra("fifo")
        if (uid <= 0 || pid <= 0 || fifo == null) {
            Timber.e("Unexpected extras: uid=[${uid}], pid=[${pid}], fifo=[${fifo}]")
            return false
        }
        output = File(fifo)
        policy = policyDB.fetch(uid) ?: SuPolicy(uid)
        try {
            pkgInfo = pm.getPackageInfo(uid, pid) ?: PackageInfo().apply {
                val name = pm.getNameForUid(uid) ?: throw PackageManager.NameNotFoundException()
                // We only fill in sharedUserId and leave other fields uninitialized
                sharedUserId = name.split(":")[0]
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e)
            respond(SuPolicy.DENY, -1)
            return false
        }
        if (!runCatching { OsConstants.S_ISFIFO(Os.lstat(output.path).st_mode) }.getOrDefault(false)) {
            Timber.e("Invalid request FIFO: $output")
            return false
        }
        return true
    }

    suspend fun respond(action: Int, time: Long) {
        policy.expiresAt = null
        policy.policy = when (action) {
            SuPolicy.ZERO -> SuPolicy.ZERO
            SuPolicy.ALLOW -> if (Config.suRestrict) SuPolicy.RESTRICT else SuPolicy.ALLOW
            SuPolicy.RESTRICT -> SuPolicy.RESTRICT
            else -> SuPolicy.DENY
        }
        if (time >= 0) {
            policy.remain = TimeUnit.MINUTES.toSeconds(time)
        } else {
            policy.remain = time
        }

        withContext(Dispatchers.IO) {
            try {
                val fd = Os.open(output.path,
                    OsConstants.O_WRONLY or OsConstants.O_NONBLOCK or
                        OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW, 0)
                try {
                    if (!OsConstants.S_ISFIFO(Os.fstat(fd).st_mode)) {
                        Timber.e("Request path is no longer a FIFO")
                        return@withContext
                    }
                    // A four-byte write is atomic for a FIFO. The daemon converts the
                    // response from its native POD representation with from_be(), so the
                    // Android side must retain the protocol's big-endian encoding.
                    val response = ByteBuffer.allocate(Int.SIZE_BYTES)
                        .putInt(policy.policy)
                        .array()
                    check(Os.write(fd, response, 0, response.size) == response.size)
                } finally {
                    Os.close(fd)
                }
            } catch (e: Exception) {
                Timber.e(e)
                // A timed-out/closed FIFO must not leave behind an unseen permanent grant.
                return@withContext
            }
            if (time >= 0 && action != SuPolicy.ZERO) {
                try {
                    policyDB.update(policy)
                    SuEvents.notifyPolicyChanged()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The current request has already been answered. Do not claim the
                    // policy was saved; subsequent requests must be evaluated again.
                    Timber.e(e)
                }
            }
        }
    }
}
