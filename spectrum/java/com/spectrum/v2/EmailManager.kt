package com.spectrum.v2

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class EmailProvider(val id: String, val name: String, val emoji: String)
data class EmailSession(
    val address: String,
    val providerId: String,   // "olamzi" | "fakemail"
    val token: String = "",
    val login: String = "",
    val domain: String = "",
    val password: String = "" // khusus Olamzi
)
data class InboxItem(val id: String, val from: String, val subject: String, val body: String)

object EmailManager {

    // ── Provider IDs ──
    const val PROVIDER_OLAMZI   = "olamzi"
    const val PROVIDER_FAKEMAIL = "fakemail"

    // Domain Olamzi = fixed (pakai domain bawaan dari API)
    // Domain FakeMail = pilihan user
    val fakemailDomains = listOf(
        "royzip.xyz",
        "fakemail.my.id",
        "sedee.xyz"
    )

    val providers = listOf(
        EmailProvider(PROVIDER_OLAMZI,   "Olamzi",        "⚡"),
        EmailProvider(PROVIDER_FAKEMAIL, "FakeMail.my.id","⭐")
    )

    fun randomUser(): String {
        val c = "abcdefghijklmnopqrstuvwxyz"
        val n = "0123456789"
        return (1..6).map { c.random() }.joinToString("") +
               (1..4).map { n.random() }.joinToString("")
    }

    fun extractOTP(text: String): String? {
        val clean = text
            .replace("<[^>]*>".toRegex(), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&#[0-9]+;".toRegex(), " ")
            .trim()

        val patterns = listOf(
            Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])"),
            Pattern.compile("(?<![0-9])([0-9]{5})(?![0-9])"),
            Pattern.compile("(?<![0-9])([0-9]{4})(?![0-9])")
        )
        for (p in patterns) {
            val m = p.matcher(clean)
            while (m.find()) {
                val g = m.group(1) ?: continue
                if (g.length == 4 && g.matches(Regex("(19|20)[0-9]{2}"))) continue
                return g
            }
        }
        return null
    }

    // ══════════════════════════════════════════
    //  GENERATE EMAIL
    // ══════════════════════════════════════════

    suspend fun generateEmail(providerId: String, domain: String = ""): EmailSession =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when (providerId) {
                PROVIDER_OLAMZI   -> generateOlamzi()
                PROVIDER_FAKEMAIL -> generateFakemail(domain)
                else              -> generateOlamzi()
            }
        }

    // ── Olamzi: GET /generate → { email, password, expires_at } ──
    private fun generateOlamzi(): EmailSession {
        return try {
            val res  = get("https://email-temp-api.olamzi.com/generate")
            val json = JSONObject(res)
            val email = json.optString("email", "")
            val pass  = json.optString("password", "")
            if (email.isNotEmpty()) {
                EmailSession(
                    address    = email,
                    providerId = PROVIDER_OLAMZI,
                    login      = email.substringBefore("@"),
                    domain     = email.substringAfter("@"),
                    password   = pass
                )
            } else {
                // fallback random jika API gagal
                val user = randomUser()
                EmailSession("$user@usererva.com", PROVIDER_OLAMZI,
                    login = user, domain = "usererva.com")
            }
        } catch (e: Exception) {
            val user = randomUser()
            EmailSession("$user@usererva.com", PROVIDER_OLAMZI,
                login = user, domain = "usererva.com")
        }
    }

    // ── FakeMail: pakai domain pilihan user ──
    private fun generateFakemail(domain: String): EmailSession {
        val d = domain.ifEmpty { fakemailDomains.first() }
        return try {
            val res  = get("https://fakemail.my.id/api/email/generate")
            val json = JSONObject(res)
            if (json.optBoolean("success")) {
                val user = json.optString("email", "").substringBefore("@")
                EmailSession("$user@$d", PROVIDER_FAKEMAIL, login = user, domain = d)
            } else {
                val user = randomUser()
                EmailSession("$user@$d", PROVIDER_FAKEMAIL, login = user, domain = d)
            }
        } catch (e: Exception) {
            val user = randomUser()
            EmailSession("$user@$d", PROVIDER_FAKEMAIL, login = user, domain = d)
        }
    }

    // ══════════════════════════════════════════
    //  CEK INBOX
    // ══════════════════════════════════════════

    suspend fun checkInbox(session: EmailSession): List<InboxItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when (session.providerId) {
                PROVIDER_OLAMZI   -> checkInboxOlamzi(session.address)
                PROVIDER_FAKEMAIL -> checkInboxFakemail(session.address)
                else              -> emptyList()
            }
        }

    // ── Olamzi: GET /inbox/{email} → { messages: [{date,from,subject,text,html}] } ──
    private fun checkInboxOlamzi(email: String): List<InboxItem> {
        return try {
            val encoded = java.net.URLEncoder.encode(email, "UTF-8")
            val res  = get("https://email-temp-api.olamzi.com/inbox/$encoded")
            val json = JSONObject(res)
            val msgs = json.optJSONArray("messages") ?: return emptyList()
            (0 until msgs.length()).map {
                val o = msgs.getJSONObject(it)
                // Olamzi: text + subject sudah lengkap, tidak perlu endpoint terpisah
                val body = o.optString("text", "") + " " + o.optString("html", "")
                InboxItem(
                    id      = it.toString(),
                    from    = o.optString("from", ""),
                    subject = o.optString("subject", ""),
                    body    = body
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ── FakeMail: GET /api/email/inbox?email={email} ──
    private fun checkInboxFakemail(email: String): List<InboxItem> {
        return try {
            val res  = get("https://fakemail.my.id/api/email/inbox?email=$email")
            val json = JSONObject(res)
            if (!json.optBoolean("success")) return emptyList()
            val msgs = json.optJSONArray("messages") ?: return emptyList()
            (0 until msgs.length()).map {
                val o = msgs.getJSONObject(it)
                InboxItem(
                    id      = o.optInt("uid").toString(),
                    from    = o.optString("from_email", ""),
                    subject = o.optString("subject", ""),
                    body    = ""
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ── Baca body email FakeMail (Olamzi sudah include body di inbox) ──
    suspend fun readEmail(session: EmailSession, uid: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (session.providerId == PROVIDER_FAKEMAIL) {
                try {
                    val res  = get("https://fakemail.my.id/api/email/show?email=${session.address}&uid=$uid")
                    val json = JSONObject(res)
                    if (!json.optBoolean("success")) return@withContext ""
                    json.optString("body", "") + " " +
                    json.optString("subject", "") + " " +
                    json.optString("body_html", "")
                } catch (e: Exception) { "" }
            } else {
                // Olamzi body sudah ada di InboxItem.body
                ""
            }
        }

    fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        } finally {
            conn.disconnect()
        }
    }
}
