package com.spectrum.v2

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var floatView: View
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    // Memory sementara - hilang setelah SAVE
    private var tempSecret = ""
    private var tempPhone  = ""

    private var currentSession: EmailSession? = null
    private var isCollapsed = false
    private var pollingJob: Job? = null
    private var lastInboxSize = 0

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragInitX  = 0
    private var dragInitY  = 0
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("LitePro", MODE_PRIVATE)
        startFg()
        buildFloat()
    }

    // ── Notifikasi silent ──
    private fun startFg() {
        val ch = "litepro"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(ch, "LitePro", NotificationManager.IMPORTANCE_MIN)
            c.setShowBadge(false)
            c.enableLights(false)
            c.enableVibration(false)
            c.setSound(null, null)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
        startForeground(1, NotificationCompat.Builder(this, ch)
            .setContentTitle("LitePro")
            .setSmallIcon(android.R.drawable.star_on)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .build())
    }

    // ── Build floating window ──
    private fun buildFloat() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null)
        val scale = prefs.getFloat("float_scale", 1.0f)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).also { it.gravity = Gravity.TOP or Gravity.END; it.x = 0; it.y = 300 }
        wm.addView(floatView, params)
        applyScale(scale)
        applyTheme()
        setupDrag()
        setupButtons()
    }

    private fun applyScale(scale: Float) {
        val px = { dp: Float -> (dp * scale * resources.displayMetrics.density).toInt() }
        listOf(R.id.fabEmail, R.id.fabNama, R.id.fabPass, R.id.fabPhone,
               R.id.fabSave, R.id.fab2FA, R.id.fabClear, R.id.fabPhoto,
               R.id.fabGear, R.id.fabClose, R.id.fabXClose)
            .forEach { id ->
                floatView.findViewById<View>(id)?.let { v ->
                    val lp = v.layoutParams
                    lp.width = px(38f); lp.height = px(38f); v.layoutParams = lp
                    val p = px(8f); v.setPadding(p, p, p, p)
                }
            }
    }

    private fun applyTheme() {
        val t = Themes.all[prefs.getInt("theme_idx", 0)]
        // BBM icon tidak perlu setColors
        listOf(R.id.fabEmail, R.id.fabNama, R.id.fabPass, R.id.fabPhone,
               R.id.fabSave, R.id.fab2FA, R.id.fabClear, R.id.fabPhoto, R.id.fabGear, R.id.fabXClose).forEach { id ->
            val d = android.graphics.drawable.GradientDrawable()
            d.shape = android.graphics.drawable.GradientDrawable.OVAL
            d.setColor((t.accentColor and 0x00FFFFFF) or 0xCC000000.toInt())
            floatView.findViewById<View>(id)?.background = d
        }
        val dRed = android.graphics.drawable.GradientDrawable()
        dRed.shape = android.graphics.drawable.GradientDrawable.OVAL
        dRed.setColor(0xCCcc0000.toInt())
        floatView.findViewById<View>(R.id.fabClose)?.background = dRed
    }

    private fun setupDrag() {
        // Drag HANYA dari YinYang - klik = toggle, geser = drag
        floatView.findViewById<View>(R.id.yinYangView).setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = e.rawX; dragStartY = e.rawY
                    dragInitX = params.x; dragInitY = params.y
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - dragStartX; val dy = e.rawY - dragStartY
                    if (!isDragging && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) isDragging = true
                    if (isDragging) {
                        params.x = (dragInitX - dx).toInt()
                        params.y = (dragInitY + dy).toInt()
                        wm.updateViewLayout(floatView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val fabGroup = floatView.findViewById<LinearLayout>(R.id.fabGroup)
                        val fabX = floatView.findViewById<View>(R.id.fabXClose)
                        isCollapsed = !isCollapsed
                        fabGroup.visibility = if (isCollapsed) View.GONE else View.VISIBLE
                        fabX.visibility = if (isCollapsed) View.VISIBLE else View.GONE
                    }
                    isDragging = false; true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        val fabGroup = floatView.findViewById<LinearLayout>(R.id.fabGroup)
        val fabX     = floatView.findViewById<View>(R.id.fabXClose)

        // YinYang - collapse/expand
        floatView.findViewById<View>(R.id.yinYangView).setOnClickListener {
            isCollapsed = !isCollapsed
            fabGroup.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            fabX.visibility     = if (isCollapsed) View.VISIBLE else View.GONE
        }
        fabX.setOnClickListener {
            isCollapsed = false
            fabGroup.visibility = View.VISIBLE
            fabX.visibility     = View.GONE
        }

        // ✉️ EMAIL - generate otomatis
        floatView.findViewById<View>(R.id.fabEmail).setOnClickListener {
            generateEmailAuto()
        }

        // ✏️ NAMA
        floatView.findViewById<View>(R.id.fabNama).setOnClickListener {
            val nama = NamaGenerator.generate(prefs)
            copy(nama); toast("✏ $nama")
        }

        // 🔑 PASSWORD - copy dari storage, notif isi password langsung
        floatView.findViewById<View>(R.id.fabPass).setOnClickListener {
            val pass = prefs.getString("app_password", "") ?: ""
            if (pass.isEmpty()) toast("Set password di Settings dulu!")
            else { copy(pass); toast(pass) }
        }

        // 📱 PHONE - input nomor sementara di memory
        floatView.findViewById<View>(R.id.fabPhone).setOnClickListener {
            showPhoneInput()
        }

        // 💾 SAVE (klik = simpan data, long press = ekstrak cookies FB Lite)
        floatView.findViewById<View>(R.id.fabSave).setOnClickListener {
            saveToSheets()
        }
        floatView.findViewById<View>(R.id.fabSave).setOnLongClickListener {
            extractAndSaveCookies()
            true
        }

        // 🔐 2FA - input secret sementara di memory
        floatView.findViewById<View>(R.id.fab2FA).setOnClickListener {
            show2FAInput()
        }

        // 🗑️ CLEAR — hanya hapus data FB Lite (com.facebook.lite), bukan Lite Pro
        floatView.findViewById<View>(R.id.fabClear).setOnClickListener {
            clearFBLiteOnly()
        }

        // 👤 PHOTO PROFILE — download random + upload ke FB Lite
        floatView.findViewById<View>(R.id.fabPhoto).setOnClickListener {
            uploadPhotoProfile()
        }

        // ⚙️ GEAR — buka menu Spectrum (MainActivity Lite Pro)
        floatView.findViewById<View>(R.id.fabGear).setOnClickListener {
            openSpectrumMenu()
        }

        // ❌ EXIT
        floatView.findViewById<View>(R.id.fabClose).setOnClickListener { stopSelf() }
    }

    // ── ⚙️ Buka menu Spectrum (MainActivity) ──
    private fun openSpectrumMenu() {
        try {
            val intent = android.content.Intent(this, MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                         android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("❌ Tidak bisa buka menu: ${e.message}")
        }
    }

    // ── 🗑️ Clear data FB Lite SAJA — Lite Pro tidak tersentuh ──
    private fun clearFBLiteOnly() {
        // Daftar package FB Lite yang dikenal (asli + clone umum)
        val fbLitePackages = listOf(
            "com.facebook.lite",
            "com.facebook.lite.clone",
            "com.dualspace.facebook.lite",
            "com.parallel.space.facebook.lite"
        )

        // Cek package mana yang terinstall
        val installed = fbLitePackages.filter { pkg ->
            try {
                packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) { false }
        }

        // Cek juga dari prefs kalau user sudah set manual
        val manualPkg = prefs.getString("target_package", "") ?: ""

        when {
            manualPkg.isNotEmpty() -> {
                // Gunakan package yang sudah diset user
                confirmClear(manualPkg)
            }
            installed.size == 1 -> {
                // Hanya 1 FB Lite → langsung clear
                confirmClear(installed[0])
            }
            installed.size > 1 -> {
                // Ada beberapa → tanya user
                val items = installed.toTypedArray()
                val d = android.app.AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle("🗑 Pilih FB Lite untuk dihapus datanya")
                    .setItems(items) { _, which -> confirmClear(items[which]) }
                    .setNegativeButton("Batal", null)
                    .create()
                d.window?.setType(overlayType())
                d.show()
            }
            else -> {
                // Tidak ada yang dikenal → tanya package manual
                showClearInput()
            }
        }
    }

    private fun confirmClear(pkg: String) {
        val d = android.app.AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("🗑 Hapus Data FB Lite?")
            .setMessage("Package: $pkg\n\nData FB Lite akan dihapus.\nLite Pro tidak terpengaruh.")
            .setPositiveButton("HAPUS") { _, _ -> clearApp(pkg) }
            .setNegativeButton("Batal", null)
            .create()
        d.window?.setType(overlayType())
        d.show()
    }

    // ── 👤 UPLOAD FOTO PROFIL — download random + upload ke FB + shortcut info kontak ──
    private fun uploadPhotoProfile() {
        scope.launch {
            if (SvAccessibilityService.instance == null) {
                toast("⚠️ Aktifkan Aksesibilitas Lite Pro dulu!\nPengaturan > Aksesibilitas > Lite Pro")
                openAccessibilitySettings()
                return@launch
            }
            showUploadNotif("📸 Download foto...")
            toast("📸 Download foto random...")
            val photo = FacebookPhotoUploader.downloadRandomPhoto(this@FloatingService)
            if (photo == null) {
                cancelUploadNotif()
                toast("❌ Gagal download foto! Cek internet.")
                return@launch
            }
            showUploadNotif("🤖 Navigasi ke profil FB Lite...")
            toast("🤖 Menuju profil FB Lite...")
            val (ok, msg) = FacebookPhotoUploader.uploadViaAccessibility(photo)
            cancelUploadNotif()
            toast(msg)
            if (ok) {
                FacebookPhotoUploader.deleteTempFile(photo)
            } else {
                try { photo.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val i = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (_: Exception) {}
    }

    private fun openAccountsCenter(packageName: String) {
        val urls = listOf(
            "https://accountscenter.facebook.com/",
            "https://m.facebook.com/settings/account_center/"
        )
        for (url in urls) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(url)
                    setPackage(packageName)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://accountscenter.facebook.com/")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }


    private fun showUploadNotif(msg: String) {
        try {
            val ch = "lp_photo"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val c = android.app.NotificationChannel(ch, "Photo Upload", android.app.NotificationManager.IMPORTANCE_LOW)
                c.setSound(null, null); c.enableVibration(false)
                getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(c)
            }
            val notif = androidx.core.app.NotificationCompat.Builder(this, ch)
                .setContentTitle("📸 Lite Pro — Upload Foto")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(msg.contains("Mengunduh") || msg.contains("Membuka"))
                .setAutoCancel(!msg.contains("Mengunduh") && !msg.contains("Membuka"))
                .build()
            getSystemService(android.app.NotificationManager::class.java).notify(4, notif)
        } catch (_: Exception) {}
    }

    private fun cancelUploadNotif() {
        try { getSystemService(android.app.NotificationManager::class.java).cancel(4) } catch (_: Exception) {}
    }

    // ── 📱 PHONE INPUT - simpan sementara di memory ──
    private fun showPhoneInput() {
        val et = makeET("Nomor / ID / Email", tempPhone)
        val d = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("📱 Nomor / ID / Email")
            .setView(et)
            .setPositiveButton("Simpan") { _, _ ->
                val v = et.text.toString().trim()
                if (v.isNotEmpty()) tempPhone = v
            }
            .setNegativeButton("Batal", null).create()
        d.window?.setType(overlayType()); d.show()
    }

    // ── 🔐 2FA INPUT - simpan sementara di memory ──
    private fun show2FAInput() {
        val et = makeET("Paste Secret 2FA disini", "")
        val d = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("🔐 2FA Generator")
            .setView(et)
            .setPositiveButton("Generate OTP") { _, _ ->
                val s = et.text.toString().trim()
                if (s.isEmpty()) return@setPositiveButton
                if (!isBase32(s)) { toast("❌ Secret tidak valid!"); return@setPositiveButton }
                try {
                    val otp = TotpGenerator.generate(s)
                    tempSecret = s
                    copy(otp)
                    toast(otp)
                } catch (e: Exception) { toast("❌ Secret tidak valid!") }
            }
            .setNegativeButton("Batal", null).create()
        d.window?.setType(overlayType()); d.show()
    }

    private fun isBase32(s: String): Boolean {
        val clean = s.uppercase().replace(" ", "").trimEnd('=')
        return clean.length >= 16 && clean.matches("[A-Z2-7]+".toRegex())
    }

    // ── 💾 SAVE TO SHEETS ──
    private fun saveToSheets() {
        val isDownload = prefs.getBoolean("save_mode_download", false)
        val pass  = prefs.getString("app_password", "") ?: ""
        val tahun = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()

        val msg = buildString {
            append("📱 Nomor: ${if(tempPhone.isNotEmpty()) tempPhone else "❌ belum diisi"}\n")
            append("🔑 Password: ${if(pass.isNotEmpty()) "✅" else "❌ belum diset"}\n")
            append("📅 Tahun: $tahun\n")
            append("🔐 2FA: ${if(tempSecret.isNotEmpty()) "✅ ada" else "tidak ada"}\n")
            append("💾 Mode: ${if(isDownload) "📥 DOWNLOAD XLSX" else "🌐 URL SHEETS"}")
        }

        val d = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("💾 Konfirmasi Save")
            .setMessage(msg)
            .setPositiveButton("SAVE") { _, _ ->
                if (isDownload) {
                    doSaveDownload(tempPhone, pass, tahun, tempSecret)
                } else {
                    val url = prefs.getString("google_script_url", "") ?: ""
                    if (url.isEmpty()) { toast("Set URL Sheets di Settings dulu!"); return@setPositiveButton }
                    doSave(url, tempPhone, pass, tahun, tempSecret)
                }
            }
            .setNegativeButton("Batal", null).create()
        d.window?.setType(overlayType()); d.show()
    }

    // ── 🍪 EKSTRAK COOKIES dari FB Lite — simpan ke CSV ──
    private fun extractAndSaveCookies() {
        toast("🍪 Mengambil cookies FB Lite...")
        scope.launch {
            val raw = FacebookPhotoUploader.extractCookies()
            if (raw.isEmpty()) {
                toast("❌ Tidak ada cookies! Login FB Lite dulu.")
                return@launch
            }
            val parts       = raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val uid         = parts.firstOrNull { it.startsWith("c_user=") }?.removePrefix("c_user=") ?: ""
            val xs          = parts.firstOrNull { it.startsWith("xs=") }?.removePrefix("xs=") ?: ""
            val cookieFmt   = parts.joinToString("|")
            val pass        = prefs.getString("app_password", "") ?: ""
            val account     = if (tempPhone.isNotEmpty()) tempPhone else uid

            val ok = withContext(Dispatchers.IO) {
                CsvManager.appendCookieRow(
                    account     = account,
                    password    = pass,
                    status      = if (uid.isNotEmpty()) "SUCCESS" else "SUCCESS_NO_UID",
                    cookies     = cookieFmt,
                    uid         = uid,
                    accessToken = xs
                )
            }
            if (ok) {
                val total = CsvManager.getTotalCookieRows()
                copy(cookieFmt)
                toast("✅ Cookies ke-$total disimpan!\nUID: $uid\n(disalin ke clipboard)")
                showSaveNotif("✅ Cookies #$total tersimpan di Download/LitePro_Cookies.csv")
            } else {
                toast("❌ Gagal simpan cookies!")
            }
        }
    }

    // ── 📥 SAVE ke CSV di folder Download ──
    private fun doSaveDownload(phone: String, pass: String, tahun: String, secret: String) {
        toast("📥 Menyimpan ke file...")
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                CsvManager.appendDataRow(phone, pass, tahun, secret)
            }
            if (success) {
                val total = CsvManager.getTotalDataRows()
                tempPhone  = ""
                tempSecret = ""
                toast("✅ Tersimpan! Total $total data di LitePro_Data.csv")
                showSaveNotif("✅ Data ke-$total tersimpan di Download/LitePro_Data.csv")
            } else {
                toast("❌ Gagal simpan ke file. Cek izin storage!")
            }
        }
    }

    private fun showSaveNotif(msg: String) {
        try {
            val ch = "lp_save"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val c = android.app.NotificationChannel(ch, "Save", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                c.setSound(null, null); c.enableVibration(false)
                getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(c)
            }
            val notif = androidx.core.app.NotificationCompat.Builder(this, ch)
                .setContentTitle("💾 Lite Pro")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(5000)
                .build()
            getSystemService(android.app.NotificationManager::class.java).notify(3, notif)
        } catch (_: Exception) {}
    }

    private fun doSave(url: String, phone: String, pass: String, tahun: String, secret: String) {
        toast("💾 Menyimpan...")
        scope.launch {
            try {
                val code = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    sb.append("phone=${URLEncoder.encode(phone, "UTF-8")}")
                    sb.append("&password=${URLEncoder.encode(pass, "UTF-8")}")
                    sb.append("&tahun=${URLEncoder.encode(tahun, "UTF-8")}")
                    if (secret.isNotEmpty())
                        sb.append("&secret=${URLEncoder.encode(secret, "UTF-8")}")
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.doOutput = true
                    conn.connectTimeout = 15000; conn.readTimeout = 15000
                    conn.outputStream.write(sb.toString().toByteArray())
                    val c = conn.responseCode; conn.disconnect(); c
                }
                if (code in 200..299) {
                    // Hapus dari memory setelah berhasil
                    tempPhone  = ""
                    tempSecret = ""
                    toast("✅ Tersimpan! Data memory dihapus.")
                } else toast("❌ Gagal HTTP $code")
            } catch (e: Exception) { toast("❌ Error: ${e.message}") }
        }
    }

    // ── ✉️ EMAIL AUTO GENERATE ──
    // ── ✉️ EMAIL AUTO GENERATE ──
    private fun generateEmailAuto() {
        val providerId = prefs.getString("email_provider", EmailManager.PROVIDER_OLAMZI) ?: EmailManager.PROVIDER_OLAMZI
        val domain     = prefs.getString("preferred_domain", EmailManager.fakemailDomains.first()) ?: EmailManager.fakemailDomains.first()
        toast("📧 Membuat email...")
        pollingJob?.cancel(); lastInboxSize = 0
        scope.launch {
            try {
                val session = EmailManager.generateEmail(providerId, domain)
                currentSession = session
                prefs.edit().putString("current_email", session.address).apply()
                copy(session.address)
                toast("✅ ${session.address}")
                startOtpPolling()
            } catch (e: Exception) { toast("❌ Gagal: ${e.message}") }
        }
    }

    private fun startOtpPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            repeat(72) {
                delay(3000)
                checkOtp()
            }
        }
    }

    private suspend fun checkOtp() {
        val session = currentSession ?: return
        try {
            val items = EmailManager.checkInbox(session)
            if (items.isNotEmpty()) {
                var foundOtp: String? = null
                for (item in items) {
                    var otp = EmailManager.extractOTP(item.subject)
                    if (otp == null) {
                        val bodySource = if (item.body.isNotEmpty()) item.body
                                         else EmailManager.readEmail(session, item.id)
                        otp = EmailManager.extractOTP(bodySource + " " + item.subject)
                    }
                    if (otp != null) { foundOtp = otp; break }
                }
                if (items.size > lastInboxSize || foundOtp != null) {
                    lastInboxSize = items.size
                    withContext(Dispatchers.Main) {
                        if (foundOtp != null) {
                            copy(foundOtp)
                            showOtpNotif(foundOtp)
                            toast(foundOtp)
                            pollingJob?.cancel()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun showOtpNotif(otp: String) {
        try {
            val ch = "lp_otp"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val c = NotificationChannel(ch, "OTP", NotificationManager.IMPORTANCE_HIGH)
                c.enableVibration(false); c.setSound(null, null)
                getSystemService(NotificationManager::class.java).createNotificationChannel(c)
            }
            val notif = androidx.core.app.NotificationCompat.Builder(this, ch)
                .setContentTitle(otp)
                .setContentText("")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(30000)
                .build()
            getSystemService(NotificationManager::class.java).notify(2, notif)
        } catch (_: Exception) {}
    }

    // ── 🗑️ CLEAR DATA ──
    private fun clearApp(pkg: String) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("pm", "clear", pkg))
            proc.waitFor()
            if (proc.exitValue() == 0) toast("🗑 Data $pkg dihapus!")
            else openAppInfo(pkg)
        } catch (e: Exception) { openAppInfo(pkg) }
    }

    private fun openAppInfo(pkg: String) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        i.data = Uri.parse("package:$pkg")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i); toast("Tap 'Hapus Data' 1x") }
        catch (e: Exception) { toast("Gagal buka App Info") }
    }

    private fun showClearInput() {
        val et = makeET("com.facebook.katana", prefs.getString("target_package","") ?: "")
        val d = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("🗑 Package Target")
            .setMessage("Klik 🗑 lagi = langsung clear!")
            .setView(et)
            .setPositiveButton("Simpan & Clear") { _, _ ->
                val pkg = et.text.toString().trim()
                if (pkg.isEmpty()) return@setPositiveButton
                prefs.edit().putString("target_package", pkg).apply()
                clearApp(pkg)
            }
            .setNegativeButton("Batal", null).create()
        d.window?.setType(overlayType()); d.show()
    }

    private fun makeET(hint: String, prefill: String = "") = EditText(this).apply {
        this.hint = hint; setText(prefill)
        setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF888888.toInt())
        setBackgroundColor(0xFF1a1a2e.toInt()); setPadding(40, 24, 40, 24)
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun copy(t: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("litepro", t))
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel(); pollingJob?.cancel()
        if (::floatView.isInitialized) try { wm.removeView(floatView) } catch (_: Exception) {}
    }
    override fun onBind(i: Intent?) = null
}
