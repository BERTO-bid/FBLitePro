package com.spectrum.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class FbLoginService : Service() {

    companion object {
        const val ACTION_START   = "com.spectrum.v2.FB_LOGIN_START"
        const val EXTRA_ACCOUNTS = "accounts"
        const val EXTRA_PASSWORD = "password"
        private const val CHANNEL_ID = "fb_login_ch"
        private const val NOTIF_ID   = 9901
        private const val TAG        = "FbLoginService"
        private const val FB_URL     = "https://m.facebook.com/login"

        fun start(ctx: Context, accounts: List<String>, password: String) {
            val i = Intent(ctx, FbLoginService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_ACCOUNTS, ArrayList(accounts))
                putExtra(EXTRA_PASSWORD, password)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i)
            else
                ctx.startService(i)
        }
    }

    private val scope   = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val results = mutableListOf<CookieResult>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
        startForeground(NOTIF_ID, buildNotif("Menyiapkan login FB..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val accounts = intent.getStringArrayListExtra(EXTRA_ACCOUNTS) ?: return START_NOT_STICKY
            val password = intent.getStringExtra(EXTRA_PASSWORD)         ?: return START_NOT_STICKY
            scope.launch { processAll(accounts, password) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Proses semua akun satu per satu - TIDAK paralel
    // Tiap akun: clear cookies -> login -> ambil cookies -> simpan CSV -> lanjut akun berikutnya
    private suspend fun processAll(accounts: List<String>, password: String) {
        val valid = accounts.filter { it.isNotBlank() }

        valid.forEachIndexed { idx, account ->
            updateNotif("Login ${idx + 1}/${valid.size}: $account")

            // WAJIB bersihkan semua cookies & data web SEBELUM tiap akun baru
            withContext(Dispatchers.IO) { clearAllWebData() }
            delay(800L)

            val result = loginOneAccount(account.trim(), password.trim())
            results.add(result)

            // Simpan ke CSV langsung setelah tiap akun
            withContext(Dispatchers.IO) {
                CsvManager.appendCookieRow(
                    account     = result.account,
                    password    = result.password,
                    status      = result.status,
                    cookies     = result.cookies,
                    uid         = result.uid,
                    accessToken = result.accessToken
                )
            }

            Log.d(TAG, "[${account}] => ${result.status}")
            delay(2000L)
        }

        val success    = results.count { it.status.startsWith("SUCCESS") }
        val checkpoint = results.count { it.status == "CHECKPOINT" }
        val error      = results.count { it.status.startsWith("ERROR") }

        updateNotif("Selesai! SUCCESS:$success CHECKPOINT:$checkpoint ERROR:$error — CSV tersimpan")

        withContext(Dispatchers.IO) { clearAllWebData() }
        delay(5000L)
        stopSelf()
    }

    // Login 1 akun dengan WebView bersih
    private suspend fun loginOneAccount(email: String, password: String): CookieResult =
        suspendCancellableCoroutine { cont ->

            val wv = WebView(applicationContext)

            wv.settings.apply {
                javaScriptEnabled                = true
                loadWithOverviewMode             = false
                useWideViewPort                  = false
                domStorageEnabled                = true
                databaseEnabled                  = false
                geolocationEnabled               = false
                allowFileAccess                  = false
                allowContentAccess               = false
                loadsImagesAutomatically         = false
                blockNetworkImage                = true
                setSupportZoom(false)
                builtInZoomControls              = false
                displayZoomControls              = false
                cacheMode                        = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = true
                userAgentString = "Mozilla/5.0 (Linux; Android 11; Redmi Note 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            val cookieMgr = CookieManager.getInstance()
            cookieMgr.removeAllCookies(null)
            cookieMgr.flush()
            cookieMgr.setAcceptCookie(true)
            cookieMgr.setAcceptThirdPartyCookies(wv, true)

            var loginAttempted = false
            var done           = false

            val timeoutJob = scope.launch {
                delay(35_000L)
                if (!done) {
                    done = true
                    wv.destroy()
                    if (cont.isActive) cont.resume(CookieResult(email, password, "ERROR: Timeout 35s"))
                }
            }

            wv.webViewClient = object : WebViewClient() {

                // Blokir semua resource berat - gambar, font, analytics, iklan
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    val block = url.contains("fbcdn.net")   ||
                                url.contains("scontent")    ||
                                url.endsWith(".jpg")        ||
                                url.endsWith(".png")        ||
                                url.endsWith(".gif")        ||
                                url.endsWith(".webp")       ||
                                url.endsWith(".svg")        ||
                                url.endsWith(".woff")       ||
                                url.endsWith(".woff2")      ||
                                url.contains("font")        ||
                                url.contains("analytics")   ||
                                url.contains("logging")     ||
                                url.contains("beacon")      ||
                                url.contains("/ads/")       ||
                                url.contains("pixel")       ||
                                url.contains("tracking")
                    return if (block)
                        WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                    else null
                }

                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "[$email] page: $url")

                    when {
                        // Halaman login - isi form
                        !loginAttempted && (
                            url.contains("m.facebook.com/login") ||
                            url.contains("m.facebook.com/r.php") ||
                            url.contains("facebook.com/login")
                        ) -> {
                            loginAttempted = true
                            val safeEmail = email.replace("'", "\\'").replace("\\", "\\\\")
                            val safePass  = password.replace("'", "\\'").replace("\\", "\\\\")

                            view.postDelayed({
                                val js = """
                                    (function(){
                                        var e = document.querySelector('input[name="email"]') ||
                                                document.querySelector('input[id="email"]') ||
                                                document.querySelector('input[type="email"]');
                                        var p = document.querySelector('input[name="pass"]') ||
                                                document.querySelector('input[id="pass"]') ||
                                                document.querySelector('input[type="password"]');
                                        var b = document.querySelector('button[name="login"]') ||
                                                document.querySelector('button[data-sigil="m_login_button"]') ||
                                                document.querySelector('input[type="submit"]') ||
                                                document.querySelector('button[type="submit"]');
                                        if(e && p && b){
                                            e.value = '$safeEmail';
                                            p.value = '$safePass';
                                            ['input','change'].forEach(function(ev){
                                                e.dispatchEvent(new Event(ev,{bubbles:true}));
                                                p.dispatchEvent(new Event(ev,{bubbles:true}));
                                            });
                                            b.click();
                                            return 'CLICKED';
                                        }
                                        return 'FORM_NOT_FOUND';
                                    })();
                                """.trimIndent()
                                view.evaluateJavascript(js) { r ->
                                    Log.d(TAG, "[$email] JS: $r")
                                    if (r?.contains("NOT_FOUND") == true) {
                                        view.postDelayed({
                                            view.evaluateJavascript(js) { r2 ->
                                                Log.d(TAG, "[$email] JS retry: $r2")
                                            }
                                        }, 2000L)
                                    }
                                }
                            }, 1000L)
                        }

                        // Login berhasil
                        loginAttempted && !done && (
                            url.contains("m.facebook.com/home")        ||
                            url.contains("m.facebook.com/?sk=")        ||
                            url.contains("m.facebook.com/feed")        ||
                            url == "https://m.facebook.com/"           ||
                            url.contains("m.facebook.com/profile.php") ||
                            url.contains("m.facebook.com/me")
                        ) -> {
                            view.postDelayed({
                                if (!done) {
                                    done = true
                                    timeoutJob.cancel()

                                    val raw = cookieMgr.getCookie("https://m.facebook.com") ?: ""
                                    Log.d(TAG, "[$email] cookies: $raw")

                                    val parts = raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                                    val uid   = parts.firstOrNull { it.startsWith("c_user=") }?.removePrefix("c_user=") ?: ""
                                    val xs    = parts.firstOrNull { it.startsWith("xs=") }?.removePrefix("xs=") ?: ""
                                    val cookieFmt = parts.joinToString("|")

                                    view.destroy()
                                    if (cont.isActive) cont.resume(
                                        CookieResult(
                                            account     = email,
                                            password    = password,
                                            status      = if (uid.isNotEmpty()) "SUCCESS" else "SUCCESS_NO_UID",
                                            cookies     = cookieFmt,
                                            uid         = uid,
                                            accessToken = xs
                                        )
                                    )
                                }
                            }, 1500L)
                        }

                        // Checkpoint
                        !done && (
                            url.contains("checkpoint")       ||
                            url.contains("two_step")         ||
                            url.contains("confirmemail")     ||
                            url.contains("identity_confirm") ||
                            url.contains("recover")
                        ) -> {
                            done = true
                            timeoutJob.cancel()
                            view.destroy()
                            if (cont.isActive) cont.resume(
                                CookieResult(email, password, "CHECKPOINT")
                            )
                        }
                    }
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    Log.e(TAG, "[$email] error: $errorCode $description @ $failingUrl")
                    if (!done && failingUrl.contains("m.facebook.com")) {
                        done = true
                        timeoutJob.cancel()
                        view.destroy()
                        if (cont.isActive) cont.resume(
                            CookieResult(email, password, "ERROR: $description")
                        )
                    }
                }
            }

            wv.loadUrl(FB_URL)
        }

    // Hapus SEMUA data WebView - dipanggil sebelum dan sesudah tiap akun
    private fun clearAllWebData() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            applicationContext.cacheDir.deleteRecursively()
            listOf("webviewCache.db", "webview.db", "Cookies", "Cookies-journal").forEach {
                applicationContext.deleteDatabase(it)
            }
            val webviewCacheDir = applicationContext.getDir("webview", Context.MODE_PRIVATE)
            webviewCacheDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "clearAllWebData: ${e.message}")
        }
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "FB Login", NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spectrum V2 — FB Login")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(text))
    }
}
