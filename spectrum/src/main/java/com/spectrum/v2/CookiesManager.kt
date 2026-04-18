package com.spectrum.v2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.security.MessageDigest

data class CookieResult(
    val account: String,
    val password: String,
    val status: String,
    val cookies: String = "",
    val uid: String = "",
    val accessToken: String = ""
)

object CookiesManager {

    // Format hasil untuk copy ke clipboard
    fun formatForCopy(results: List<CookieResult>): String =
        results.joinToString("\n\n") { r ->
            buildString {
                append("Email   : ${r.account}\n")
                append("Password: ${r.password}\n")
                append("Status  : ${r.status}\n")
                if (r.uid.isNotEmpty())         append("UID     : ${r.uid}\n")
                if (r.cookies.isNotEmpty())     append("Cookies : ${r.cookies}\n")
                if (r.accessToken.isNotEmpty()) append("XS      : ${r.accessToken}")
            }.trimEnd()
        }

    // Simpan ke CSV
    fun saveToXlsx(results: List<CookieResult>): Boolean {
        return try {
            results.forEach { r ->
                CsvManager.appendCookieRow(
                    account     = r.account,
                    password    = r.password,
                    status      = r.status,
                    cookies     = r.cookies,
                    uid         = r.uid,
                    accessToken = r.accessToken
                )
            }
            true
        } catch (_: Exception) { false }
    }

    @Suppress("UNUSED")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Suppress("UNUSED")
    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
