package com.spectrum.v2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * SvAccessibilityService
 * Karena Spectrum disatukan DALAM FB Lite APK, service ini punya akses
 * penuh ke semua node UI FB Lite — klik seperti manusia.
 *
 * Fitur:
 * 1. Upload foto profil — navigasi + klik tombol edit foto + pilih file
 * 2. Ekstrak cookies    — baca CookieManager langsung (satu proses)
 */
class SvAccessibilityService : AccessibilityService() {

    companion object {
        var instance: SvAccessibilityService? = null

        var pendingPhotoPath: String? = null
        var isUploadMode = false
        var isCookieMode = false

        var onUploadDone: ((Boolean, String) -> Unit)? = null
        var onCookieExtracted: ((String) -> Unit)? = null

        fun startPhotoUpload(path: String, onDone: (Boolean, String) -> Unit) {
            pendingPhotoPath = path
            isUploadMode     = true
            isCookieMode     = false
            onUploadDone     = onDone
            instance?.navigateToProfile()
        }

        fun extractCookies(onDone: (String) -> Unit) {
            isCookieMode     = true
            isUploadMode     = false
            onCookieExtracted = onDone
            instance?.doExtractCookies()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var uploadStep = 0

    private val editPhotoKw  = listOf("edit photo","edit foto","ubah foto","change profile photo","ganti foto","update photo","photo profil")
    private val profileKw    = listOf("profil saya","my profile","lihat profil","view profile","edit profil")
    private val galleryKw    = listOf("galeri","gallery","pilih foto","choose photo","from gallery","dari galeri","upload foto")
    private val confirmKw    = listOf("simpan","save","selesai","done","gunakan foto ini","use this photo","set as profile picture","konfirmasi")

    override fun onServiceConnected() {
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes   = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                           AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            packageNames = null
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (isUploadMode) handleUploadEvent(ev)
    }

    // ══ UPLOAD STATE MACHINE ══════════════════

    private fun handleUploadEvent(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        when (uploadStep) {
            0 -> {
                val n = findByKw(root, editPhotoKw) ?: findByKw(root, listOf("edit","foto","camera","kamera"))
                if (n != null) { handler.postDelayed({ click(n); uploadStep = 1 }, 600) }
            }
            1 -> {
                val n = findByKw(root, galleryKw)
                if (n != null) { handler.postDelayed({ click(n); uploadStep = 2 }, 400) }
            }
            2 -> {
                val path = pendingPhotoPath ?: run { finish(false,"Path kosong"); return }
                if (injectPath(root, path)) { uploadStep = 3; return }
                val img = firstClickableImage(root)
                if (img != null) { handler.postDelayed({ click(img); uploadStep = 3 }, 500) }
            }
            3 -> {
                val n = findByKw(root, confirmKw)
                if (n != null) { handler.postDelayed({ click(n); uploadStep = 4 }, 500) }
            }
            4 -> {
                isUploadMode = false; uploadStep = 0
                finish(true, "✅ Foto profil berhasil diupload!")
            }
        }
    }

    fun navigateToProfile() {
        uploadStep = 0
        val intent = packageManager.getLaunchIntentForPackage("com.facebook.lite")
            ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent != null) {
            startActivity(intent)
            handler.postDelayed({
                val root = rootInActiveWindow ?: return@postDelayed
                val n = findByKw(root, profileKw) ?: findByDesc(root, listOf("profil","profile","me"))
                n?.let { click(it) }
            }, 2500)
        }
    }

    private fun finish(ok: Boolean, msg: String) {
        handler.post { onUploadDone?.invoke(ok, msg) }
        onUploadDone   = null
        pendingPhotoPath = null
    }

    // ══ COOKIE EXTRACT ════════════════════════
    // Karena satu proses dengan FB Lite, CookieManager sudah isi cookies FB

    fun doExtractCookies() {
        handler.postDelayed({
            try {
                val cm  = android.webkit.CookieManager.getInstance()
                val raw = cm.getCookie("https://m.facebook.com")
                    ?: cm.getCookie("https://www.facebook.com")
                    ?: cm.getCookie("https://facebook.com")
                    ?: ""
                isCookieMode = false
                onCookieExtracted?.invoke(raw)
                onCookieExtracted = null
            } catch (e: Exception) {
                isCookieMode = false
                onCookieExtracted?.invoke("")
                onCookieExtracted = null
            }
        }, 500)
    }

    // ══ NODE HELPERS ══════════════════════════

    private fun findByKw(root: AccessibilityNodeInfo, kws: List<String>): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>().also { it.add(root) }
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val s = "${n.text?:""} ${n.contentDescription?:""}".lowercase()
            if (kws.any { s.contains(it) } && (n.isClickable || n.isEnabled)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    private fun findByDesc(root: AccessibilityNodeInfo, kws: List<String>): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>().also { it.add(root) }
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            if (kws.any { d.contains(it) } && n.isClickable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    private fun firstClickableImage(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>().also { it.add(root) }
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if ((n.className?.contains("ImageView") == true ||
                 n.className?.contains("RecyclerView") == true) && n.isClickable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    private fun injectPath(root: AccessibilityNodeInfo, path: String): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>().also { it.add(root) }
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.className?.contains("EditText") == true) {
                val b = Bundle()
                b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, path)
                n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
                return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return false
    }

    private fun click(node: AccessibilityNodeInfo) {
        if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
        var p = node.parent; var d = 0
        while (p != null && d++ < 5) {
            if (p.isClickable) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
            p = p.parent
        }
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.centerX() > 0 && r.centerY() > 0) tapAt(r.centerX().toFloat(), r.centerY().toFloat())
    }

    private fun tapAt(x: Float, y: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(x,y) }, 0, 50))
                .build()
            dispatchGesture(g, null, null)
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); instance = null; isUploadMode = false; isCookieMode = false }
}
