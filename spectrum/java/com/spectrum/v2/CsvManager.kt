package com.spectrum.v2

import android.os.Environment
import java.io.File

/**
 * CsvManager — Simpan semua data ke CSV di folder Download
 * File: LitePro_Data.csv      → data akun (nomor, password, tahun, 2FA)
 * File: LitePro_Cookies.csv   → hasil ekstrak cookies FB
 */
object CsvManager {

    // ── File paths ──
    private const val DATA_FILE    = "LitePro_Data.csv"
    private const val COOKIES_FILE = "LitePro_Cookies.csv"

    private fun downloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun getDataFile(): File    = File(downloadsDir(), DATA_FILE)
    fun getCookiesFile(): File = File(downloadsDir(), COOKIES_FILE)

    // ══════════════════════════════════════════
    // DATA — Nomor / Password / Tahun / 2FA
    // ══════════════════════════════════════════

    fun appendDataRow(
        phone: String,
        password: String,
        tahun: String,
        secret: String
    ): Boolean {
        return try {
            val file = getDataFile()
            ensureDownloadsDir()
            if (!file.exists()) {
                file.writeText(
                    "No,Nomor / Email,Password,Tahun,2FA Secret\n",
                    Charsets.UTF_8
                )
            }
            val no  = getTotalDataRows() + 1
            val row = listOf(no.toString(), phone, password, tahun, secret)
                .joinToString(",") { csvEscape(it) }
            file.appendText(row + "\n", Charsets.UTF_8)
            true
        } catch (_: Exception) { false }
    }

    fun getTotalDataRows(): Int {
        val file = getDataFile()
        if (!file.exists()) return 0
        return try {
            file.readLines(Charsets.UTF_8).count { it.isNotBlank() } - 1 // minus header
        } catch (_: Exception) { 0 }
    }

    // ══════════════════════════════════════════
    // COOKIES — hasil login FB
    // ══════════════════════════════════════════

    fun appendCookieRow(
        account: String,
        password: String,
        status: String,
        cookies: String,
        uid: String,
        accessToken: String
    ): Boolean {
        return try {
            val file = getCookiesFile()
            ensureDownloadsDir()
            if (!file.exists()) {
                file.writeText(
                    "No,Account,Password,Status,UID,Access Token,Cookies\n",
                    Charsets.UTF_8
                )
            }
            val no  = getTotalCookieRows() + 1
            val row = listOf(no.toString(), account, password, status, uid, accessToken, cookies)
                .joinToString(",") { csvEscape(it) }
            file.appendText(row + "\n", Charsets.UTF_8)
            true
        } catch (_: Exception) { false }
    }

    fun getTotalCookieRows(): Int {
        val file = getCookiesFile()
        if (!file.exists()) return 0
        return try {
            file.readLines(Charsets.UTF_8).count { it.isNotBlank() } - 1
        } catch (_: Exception) { 0 }
    }

    // ══════════════════════════════════════════
    // UTILS
    // ══════════════════════════════════════════

    private fun ensureDownloadsDir() {
        val dir = downloadsDir()
        if (!dir.exists()) dir.mkdirs()
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }
}
