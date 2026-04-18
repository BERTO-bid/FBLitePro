package com.spectrum.v2

import android.content.SharedPreferences
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import kotlin.math.pow

object NamaGenerator {

    private fun variasiIndo(n: String, idx: Int): String = when (idx) {
        0 -> n
        1 -> n.replace("i", "ie").replace("y", "ie")
        2 -> n.replace("i", "y")
        3 -> n + "h"
        4 -> if (n.endsWith("a")) n + "h" else n + "ah"
        5 -> n.replace("k", "kh")
        6 -> n.replace("sy", "s").replace("sh", "s")
        7 -> n.replace("dh", "d").replace("th", "t")
        8 -> n + "a"
        9 -> n + "i"
        10 -> n.replace("e", "a")
        11 -> n.replace("o", "u")
        12 -> n + "n"
        13 -> n + "r"
        14 -> n.replace("f", "ph")
        15 -> n.replace("z", "s")
        16 -> n + "y"
        17 -> n.replace("c", "k")
        18 -> n + "wan"
        19 -> n + "wati"
        20 -> n.replace("ri", "ry")
        21 -> n.replace("ni", "ny")
        22 -> n + "nto"
        23 -> n + "nta"
        24 -> n.replace("ah", "a")
        else -> n
    }

    private fun variasiMalay(n: String, idx: Int): String = when (idx) {
        0 -> n
        1 -> n + "h"
        2 -> n.replace("i", "ie")
        3 -> n.replace("a", "ah")
        4 -> n + "in"
        5 -> n + "ul"
        6 -> n.replace("ie", "i")
        7 -> n + "iah"
        8 -> n.replace("z", "s")
        9 -> n + "an"
        10 -> n + "yn"
        11 -> n.replace("q", "k")
        12 -> n + "din"
        13 -> n + "uddin"
        14 -> n.replace("sy", "sh")
        15 -> n + "ra"
        16 -> n + "ri"
        17 -> n.replace("kh", "k")
        18 -> n + "rul"
        19 -> n + "zan"
        20 -> n.replace("f", "ph")
        21 -> n + "mi"
        22 -> n + "ni"
        23 -> n + "ya"
        24 -> n.replace("wh", "w")
        else -> n
    }

    private fun variasiAmerica(n: String, idx: Int): String = when (idx) {
        0 -> n
        1 -> n + "e"
        2 -> n.replace("y", "ie")
        3 -> n.replace("ie", "y")
        4 -> n + "y"
        5 -> n.replace("ph", "f")
        6 -> n.replace("f", "ph")
        7 -> n.replace("c", "k")
        8 -> n.replace("k", "c")
        9 -> n + "a"
        10 -> n.replace("er", "ar")
        11 -> n.replace("on", "an")
        12 -> n + "n"
        13 -> n.replace("nn", "n")
        14 -> n.replace("tt", "t")
        15 -> n.replace("ll", "l")
        16 -> n + "lyn"
        17 -> n + "lee"
        18 -> n.replace("ai", "ay")
        19 -> n.replace("ay", "ai")
        20 -> n + "son"
        21 -> n.replace("ch", "sh")
        22 -> n.replace("sh", "ch")
        23 -> n + "ie"
        24 -> n.replace("ck", "k")
        else -> n
    }

    fun generate(prefs: SharedPreferences): String {
        val negara = (0..2).random()
        val pool = when (negara) {
            0 -> NamaData.indonesia
            1 -> NamaData.malaysia
            else -> NamaData.america
        }
        val varCount = 25
        val negaraKey = "used_$negara"
        val usedStr = prefs.getString(negaraKey, "") ?: ""
        val used = if (usedStr.isEmpty()) mutableSetOf() else usedStr.split("|").toMutableSet()

        val allVariasi = mutableListOf<Pair<String, String>>()
        for (base in pool) {
            for (idx in 0 until varCount) {
                val key = "${base}_$idx"
                if (!used.contains(key)) {
                    val hasil = when (negara) {
                        0 -> variasiIndo(base, idx)
                        1 -> variasiMalay(base, idx)
                        else -> variasiAmerica(base, idx)
                    }.trim()
                    if (hasil.length in 3..14) {
                        allVariasi.add(Pair(key, hasil))
                    }
                }
            }
        }

        if (allVariasi.isEmpty()) {
            prefs.edit().remove(negaraKey).apply()
            return generate(prefs)
        }

        val picked = allVariasi.random()
        used.add(picked.first)
        val savedList = used.toList().takeLast(5000)
        prefs.edit().putString(negaraKey, savedList.joinToString("|")).apply()

        return picked.second.lowercase().replaceFirstChar { c -> c.uppercaseChar() }
    }
}

object TotpGenerator {
    fun generate(secret: String, digits: Int = 6, period: Long = 30): String {
        val key = base32Decode(secret.toUpperCase().replace(" ", "").trimEnd('='))
        val step = System.currentTimeMillis() / 1000 / period
        val msg = ByteBuffer.allocate(8).putLong(step).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(msg)
        val off = hash.last().toInt() and 0x0F
        val bin = ((hash[off].toInt() and 0x7F) shl 24) or
                  ((hash[off+1].toInt() and 0xFF) shl 16) or
                  ((hash[off+2].toInt() and 0xFF) shl 8) or
                   (hash[off+3].toInt() and 0xFF)
        return (bin % 10.0.pow(digits).toInt()).toString().padStart(digits, '0')
    }

    private fun base32Decode(s: String): ByteArray {
        val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0L; var bitsLen = 0
        val out = mutableListOf<Byte>()
        for (c in s) {
            val v = alpha.indexOf(c); if (v < 0) continue
            bits = (bits shl 5) or v.toLong(); bitsLen += 5
            if (bitsLen >= 8) { bitsLen -= 8; out.add(((bits shr bitsLen) and 0xFF).toByte()) }
        }
        return out.toByteArray()
    }
}
