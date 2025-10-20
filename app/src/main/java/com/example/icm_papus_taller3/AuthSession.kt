package com.example.icm_papus_taller3

import android.content.Context

object AuthSession {
    private const val PREF = "auth"

    fun save(ctx: Context, idToken: String, uid: String, email: String?) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString("idToken", idToken)
            .putString("uid", uid)
            .putString("email", email)
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun token(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("idToken", null)

    fun uid(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("uid", null)

    fun email(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("email", null)
}
