package com.topjohnwu.magisk.core.data.magiskdb

import com.topjohnwu.magisk.core.ktx.await
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class MagiskDB {

    // Only for SQL expressions assembled by the database/model layer, never user input.
    internal class Literal(val expression: String)

    suspend inline fun <R> exec(
        query: String,
        crossinline mapper: (Map<String, String>) -> R
    ): List<R> {
        return withContext(Dispatchers.IO) {
            val result = Shell.cmd("magisk --sqlite '$query'").await()
            check(result.isSuccess) { "Magisk database query failed" }
            result.out.map { line ->
                line.split("\\|".toRegex())
                    .map { it.split("=", limit = 2) }
                    .filter { it.size == 2 }
                    .associate { it[0] to it[1] }
                    .let(mapper)
            }
        }
    }

    suspend fun exec(query: String) {
        withContext(Dispatchers.IO) {
            val result = Shell.cmd("magisk --sqlite '$query'").await()
            check(result.isSuccess) { "Magisk database write failed" }
        }
    }

    fun Map<String, Any>.toQuery(): String {
        val keys = this.keys.joinToString(",")
        val values = this.values.joinToString(",") {
            when (it) {
                is Boolean -> if (it) "1" else "0"
                is Number -> it.toString()
                is Literal -> it.expression
                else -> "\"$it\""
            }
        }
        return "($keys) VALUES($values)"
    }

    object Table {
        const val POLICY = "policies"
        const val SETTINGS = "settings"
    }
}
