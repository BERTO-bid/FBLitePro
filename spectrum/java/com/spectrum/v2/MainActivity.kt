package com.spectrum.v2

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("LitePro", MODE_PRIVATE)
        applyTheme(prefs.getInt("theme_idx", 0))
        setupButtons()
    }

    private fun setupButtons() {

        // COOKIES — bulk extract cookies FB
        findViewById<Button>(R.id.btnMF).setOnClickListener { showCookiesDialog() }

        // EMAIL - pilih provider
        findViewById<Button>(R.id.btnEmail).setOnClickListener { showProviderPicker() }

        // GENERATE NAMA
        findViewById<Button>(R.id.btnGenNama).setOnClickListener {
            val nama = NamaGenerator.generate(prefs)
            copy(nama)
            Toast.makeText(this, "✏ $nama (disalin!)", Toast.LENGTH_LONG).show()
        }

        // NOMOR HP
        findViewById<Button>(R.id.btnUbahPassword).setOnClickListener {
            inputDialog("📱 Nomor HP",
                prefs.getString("app_phone","") ?: "",
                "Contoh: 6281234567890") { v ->
                prefs.edit().putString("app_phone", v).apply()
                Toast.makeText(this, "✅ Nomor HP disimpan!", Toast.LENGTH_SHORT).show()
            }
        }

        // PASSWORD - tampil plain text
        findViewById<Button>(R.id.btnSaveSheets).setOnClickListener {
            inputDialogPlain("🔑 Password",
                prefs.getString("app_password","") ?: "",
                "Masukkan password akun") { v ->
                prefs.edit().putString("app_password", v).apply()
                Toast.makeText(this, "✅ Password disimpan!", Toast.LENGTH_SHORT).show()
            }
        }

        // 2FA SECRET
        findViewById<Button>(R.id.btn2FA).setOnClickListener {
            inputDialog("🔐 Secret 2FA",
                prefs.getString("saved_2fa_secret","") ?: "",
                "Secret key (kosongkan = nonaktif)") { v ->
                if (v.isEmpty()) {
                    prefs.edit().remove("saved_2fa_secret").apply()
                    Toast.makeText(this, "2FA dinonaktifkan", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val otp = TotpGenerator.generate(v)
                        prefs.edit().putString("saved_2fa_secret", v).apply()
                        copy(otp)
                        Toast.makeText(this, "🔐 OTP: $otp — Secret disimpan!", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "❌ Secret tidak valid!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // URL SHEETS
        findViewById<Button>(R.id.btnClearData).setOnClickListener {
            inputDialog("📊 URL Google Apps Script",
                prefs.getString("google_script_url","") ?: "",
                "Paste URL script disini") { v ->
                prefs.edit().putString("google_script_url", v).apply()
                Toast.makeText(this, "✅ URL disimpan!", Toast.LENGTH_SHORT).show()
            }
        }

        // PACKAGE TARGET CLEAR DATA - pindah ke settings tersembunyi (long press btnClearData)
        findViewById<Button>(R.id.btnClearData).setOnLongClickListener {
            inputDialog("🗑 Package Target",
                prefs.getString("target_package","") ?: "",
                "Contoh: com.facebook.katana") { v ->
                prefs.edit().putString("target_package", v).apply()
                Toast.makeText(this, "✅ Package disimpan!", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // MODE SIMPAN
        val btnMode = findViewById<Button>(R.id.btnSaveMode)
        updateModeBtnLabel(btnMode)
        btnMode.setOnClickListener {
            val isDownload = prefs.getBoolean("save_mode_download", false)
            val newMode = !isDownload
            prefs.edit().putBoolean("save_mode_download", newMode).apply()
            updateModeBtnLabel(btnMode)
            val msg = if (newMode)
                "📥 Mode: DOWNLOAD — data disimpan ke\nDownload/LitePro_Data.csv"
            else
                "🌐 Mode: URL SHEETS — data dikirim ke Google Sheets"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnThemes).setOnClickListener { showThemeDialog() }
        findViewById<Button>(R.id.btnFlot).setOnClickListener { showFlotDialog() }
    }



    // ══════════════════════════════════════════
    // DIALOG COOKIES — login FB background + ambil cookies
    // ══════════════════════════════════════════
    private fun showCookiesDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 16, 40, 8)
        }

        val tvAccLabel = android.widget.TextView(this).apply {
            text = "Email / Nomor (1 per baris)"
            setTextColor(0xFFaaaaaa.toInt())
            textSize = 12f
            setPadding(0, 8, 0, 4)
        }
        val etAccounts = EditText(this).apply {
            hint = "contoh@email.com"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF555555.toInt())
            setBackgroundColor(0xFF0d0d1a.toInt())
            setPadding(24, 16, 24, 16)
            minLines = 4
            maxLines = 8
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
        }

        val tvPassLabel = android.widget.TextView(this).apply {
            text = "Password (sama untuk semua)"
            setTextColor(0xFFaaaaaa.toInt())
            textSize = 12f
            setPadding(0, 12, 0, 4)
        }
        val etPassword = EditText(this).apply {
            hint = "Password akun FB"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF555555.toInt())
            setBackgroundColor(0xFF0d0d1a.toInt())
            setPadding(24, 16, 24, 16)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }

        val tvStatus = android.widget.TextView(this).apply {
            text = "Masukkan akun lalu klik GET"
            setTextColor(0xFF888888.toInt())
            textSize = 12f
            setPadding(0, 12, 0, 0)
        }

        layout.addView(tvAccLabel)
        layout.addView(etAccounts)
        layout.addView(tvPassLabel)
        layout.addView(etPassword)
        layout.addView(tvStatus)

        val dialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Login FB + Ambil Cookies")
            .setView(layout)
            .setPositiveButton("GET") { _, _ -> }
            .setNegativeButton("Tutup", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val btnGet = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnGet.setTextColor(0xFF00ff88.toInt())

            btnGet.setOnClickListener {
                val raw      = etAccounts.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (raw.isEmpty()) {
                    Toast.makeText(this, "Masukkan email/nomor dulu!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password.isEmpty()) {
                    Toast.makeText(this, "Masukkan password dulu!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val accounts = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

                FbLoginService.start(this@MainActivity, accounts, password)

                tvStatus.text = "Berjalan di background...\n${accounts.size} akun diproses\nHasil tersimpan otomatis ke CSV\nCek notifikasi untuk progress"
                tvStatus.setTextColor(0xFF00ff88.toInt())

                Toast.makeText(this@MainActivity,
                    "Login berjalan di background!",
                    Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }

    private fun showProviderPicker() {
        val providers = EmailManager.providers
        val curProvider = prefs.getString("email_provider", EmailManager.PROVIDER_OLAMZI) ?: EmailManager.PROVIDER_OLAMZI
        val curIdx = providers.indexOfFirst { it.id == curProvider }.let { if (it < 0) 0 else it }
        val names = providers.map { "${it.emoji} ${it.name}" }.toTypedArray()

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("📧 Pilih Provider Email")
            .setSingleChoiceItems(names, curIdx) { dialog, which ->
                val selected = providers[which]
                prefs.edit().putString("email_provider", selected.id).apply()
                dialog.dismiss()

                // Jika FakeMail, tampilkan pilihan domain
                if (selected.id == EmailManager.PROVIDER_FAKEMAIL) {
                    showDomainPicker()
                } else {
                    // Olamzi tidak perlu pilih domain
                    Toast.makeText(this, "✅ Provider: ${selected.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Tutup", null).show()
    }

    private fun showDomainPicker() {
        val domains = EmailManager.fakemailDomains
        val cur = prefs.getString("preferred_domain", domains.first()) ?: domains.first()
        val curIdx = domains.indexOfFirst { it == cur }.let { if (it < 0) 0 else it }
        val names = domains.map { "📧 @$it" }.toTypedArray()
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("📧 Pilih Domain FakeMail")
            .setSingleChoiceItems(names, curIdx) { dialog, which ->
                prefs.edit().putString("preferred_domain", domains[which]).apply()
                dialog.dismiss()
                Toast.makeText(this, "✅ @${domains[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Tutup", null).show()
    }

    private fun showThemeDialog() {
        val names = Themes.all.map { "#${String.format("%02d",it.id)} ${it.name}" }.toTypedArray()
        val cur = prefs.getInt("theme_idx", 0)
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("🎨 Pilih Tema")
            .setSingleChoiceItems(names, cur) { dialog, which ->
                prefs.edit().putInt("theme_idx", which).apply()
                applyTheme(which); dialog.dismiss()
                stopService(Intent(this, FloatingService::class.java))
                if (canOverlay()) startFloat()
            }
            .setNegativeButton("Tutup", null).show()
    }

    private fun showFlotDialog() {
        val sizes = floatArrayOf(0.5f,0.7f,1.0f,1.3f,1.5f,2.0f)
        val labels = sizes.map { "${it}x" }.toTypedArray()
        val cur = prefs.getFloat("float_scale", 1.0f)
        val curIdx = sizes.indexOfFirst { Math.abs(it-cur)<0.05f }.let { if(it<0) 2 else it }
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("📐 Ukuran Floating")
            .setSingleChoiceItems(labels, curIdx) { dialog, which ->
                prefs.edit().putFloat("float_scale", sizes[which]).apply()
                dialog.dismiss()
                stopService(Intent(this, FloatingService::class.java))
                if (canOverlay()) startFloat()
            }
            .setNegativeButton("Tutup", null).show()
    }

    private fun inputDialog(title: String, current: String, hint: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(current); this.hint = hint
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt())
            setPadding(40, 24, 40, 24)
        }
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton("Batal", null).show()
    }

    // Input dialog plain text - tidak ada bintang
    private fun inputDialogPlain(title: String, current: String, hint: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(current); this.hint = hint
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt())
            setPadding(40, 24, 40, 24)
            // Plain text - tidak disensor bintang
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton("Batal", null).show()
    }


    private fun updatePkgBtnLabel(btn: Button) {
        val isManual = prefs.getBoolean("pkg_mode_manual", false)
        val manualPkg = prefs.getString("pkg_manual", "") ?: ""
        if (isManual && manualPkg.isNotEmpty()) {
            btn.text = "PKG: $manualPkg"
            btn.setBackgroundColor(0xFFcc6600.toInt())
        } else {
            btn.text = "PKG: AUTO DETECT"
            btn.setBackgroundColor(0xFF006633.toInt())
        }
        btn.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun showPackageDialog(btn: Button) {
        val isManual = prefs.getBoolean("pkg_mode_manual", false)
        val manualPkg = prefs.getString("pkg_manual", "") ?: ""
        val options = arrayOf(
            "AUTO DETECT (FB Lite / FB Original)",
            "MANUAL - Isi Package Name Sendiri"
        )
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Package Name FB")
            .setSingleChoiceItems(options, if (isManual) 1 else 0) { dialog, which ->
                if (which == 0) {
                    prefs.edit().putBoolean("pkg_mode_manual", false).apply()
                    updatePkgBtnLabel(btn)
                    dialog.dismiss()
                    Toast.makeText(this, "Mode AUTO aktif", Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                    val input = EditText(this).apply {
                        hint = "com.facebook.lite"
                        setText(manualPkg)
                        setTextColor(0xFFFFFFFF.toInt())
                        setHintTextColor(0xFF888888.toInt())
                        setBackgroundColor(0xFF1a1a2e.toInt())
                        setPadding(40, 24, 40, 24)
                    }
                    AlertDialog.Builder(this, R.style.DialogTheme)
                        .setTitle("Isi Package Name")
                        .setMessage("Contoh:\ncom.facebook.lite\ncom.facebook.katana\ncom.gb.fbpro")
                        .setView(input)
                        .setPositiveButton("SIMPAN") { _, _ ->
                            val pkg = input.text.toString().lowercase().trim()
                            if (pkg.isNotEmpty()) {
                                prefs.edit()
                                    .putBoolean("pkg_mode_manual", true)
                                    .putString("pkg_manual", pkg)
                                    .apply()
                                updatePkgBtnLabel(btn)
                                Toast.makeText(this, "Package: $pkg", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("BATAL", null).show()
                }
            }.setNegativeButton("TUTUP", null).show()
    }

    // ── FB MODE: Lite atau Katana ──
    private fun updateFBModeBtnLabel(btn: Button) {
        val savedPkg   = prefs.getString("fb_target_pkg", "") ?: ""
        val savedLabel = prefs.getString("fb_target_label", "") ?: ""
        if (savedPkg.isNotEmpty()) {
            btn.text = "📱 FB: $savedLabel"
            btn.setBackgroundColor(0xFF004488.toInt())
        } else {
            btn.text = "📱 PILIH APP FB"
            btn.setBackgroundColor(0xFF880000.toInt())
        }
        btn.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun showFBModeDialog(btn: Button) {
        val pm = packageManager

        // Tampilkan SEMUA app yang diinstall user — bukan system app
        // Termasuk clone, dual space, mod, apapun
        val allApps = pm.getInstalledApplications(0)
            .filter { app ->
                (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                Pair(app.packageName, label)
            }
            .sortedBy { it.second.lowercase() }

        if (allApps.isEmpty()) {
            Toast.makeText(this, "❌ Tidak ada app terinstall!", Toast.LENGTH_SHORT).show()
            return
        }

        // Tampilan: "Nama App\ncom.package.name"
        val displayList = allApps.map { "${it.second}\n${it.first}" }.toTypedArray()

        // Tandai yang sudah dipilih sebelumnya
        val savedPkg = prefs.getString("fb_target_pkg", "") ?: ""
        val currentIdx = allApps.indexOfFirst { it.first == savedPkg }

        android.app.AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Pilih App FB / Clone")
            .setSingleChoiceItems(displayList, currentIdx) { dialog, which ->
                val selectedPkg   = allApps[which].first
                val selectedLabel = allApps[which].second
                prefs.edit()
                    .putString("fb_target_pkg", selectedPkg)
                    .putString("fb_target_label", selectedLabel)
                    .apply()
                updateFBModeBtnLabel(btn)
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "✅ $selectedLabel\n$selectedPkg",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("TUTUP", null)
            .show()
    }

    private fun updateModeBtnLabel(btn: Button) {
        val isDownload = prefs.getBoolean("save_mode_download", false)
        btn.text = if (isDownload) "📥 MODE: DOWNLOAD XLSX" else "🌐 MODE: URL SHEETS"
        btn.setBackgroundColor(if (isDownload) 0xFF006633.toInt() else 0xFF330088.toInt())
    }

    fun applyTheme(idx: Int) {
        val t = Themes.all[idx]
        findViewById<View>(R.id.rootLayout).setBackgroundColor(t.phoneBg)
        findViewById<View>(R.id.appBar).setBackgroundColor(t.appBarBg)
        findViewById<TextView>(R.id.tvAppTitle).setTextColor(t.appBarColor)
        findViewById<TextView>(R.id.tvVersion).setTextColor(t.vTagColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) window.statusBarColor = t.statusBg
        listOf(R.id.btnEmail, R.id.btnGenNama, R.id.btnUbahPassword,
               R.id.btnSaveSheets, R.id.btn2FA, R.id.btnClearData)
            .forEachIndexed { i, id ->
                val b = findViewById<Button>(id)
                b.setBackgroundColor(if(i%2==0) t.btn1Bg else t.btn2Bg)
                b.setTextColor(t.btnColor)
            }
        listOf(R.id.btnThemes to t.smallBtn1Bg, R.id.btnFlot to t.smallBtn2Bg).forEach { (id,bg) ->
            val b = findViewById<Button>(id); b.setBackgroundColor(bg); b.setTextColor(t.btnColor)
        }
    }

    private fun canOverlay() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    private fun startFloat() {
        val i = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun copy(t: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("sv2", t))
    }

    override fun onResume() {
        super.onResume()
        if (canOverlay()) startFloat()
        else AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Izin Diperlukan")
            .setMessage("Aktifkan izin 'Tampil di atas aplikasi lain'")
            .setPositiveButton("Buka") { _,_ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
            }.setNegativeButton("Nanti", null).show()

    }
}
