package com.example.wandremote.network

import android.content.Context

data class RecentPc(val name: String, val host: String, val port: Int)

/** Last 5 connected PCs, stored in SharedPreferences as "host:port:name" lines. */
class RecentPcs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("recent_pcs", Context.MODE_PRIVATE)

    fun load(): List<RecentPc> =
        (prefs.getString(KEY, "") ?: "").lines().mapNotNull { line ->
            val p = line.split(':', limit = 3)
            val port = p.getOrNull(1)?.toIntOrNull()
            if (p.size == 3 && port != null) RecentPc(p[2], p[0], port) else null
        }

    fun add(pc: RecentPc): List<RecentPc> {
        val list = (listOf(pc) + load().filterNot { it.host == pc.host && it.port == pc.port }).take(MAX)
        prefs.edit()
            .putString(KEY, list.joinToString("\n") { "${it.host}:${it.port}:${it.name.replace('\n', ' ')}" })
            .apply()
        return list
    }

    private companion object {
        const val KEY = "list"
        const val MAX = 5
    }
}
