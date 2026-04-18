# FB Lite Pro — FB Lite + Spectrum (1 APK)

## Struktur Repo

```
├── fblite/
│   └── Facebook_Lite.apk          ← LETAKKAN APK FB LITE DI SINI
├── spectrum/
│   ├── java/com/spectrum/v2/       ← source code Spectrum (Kotlin)
│   ├── res/                        ← resources Spectrum
│   ├── AndroidManifest.xml
│   └── SPECTRUM_MANIFEST_INJECT.xml
├── .github/workflows/main.yml      ← GitHub Actions: auto build
└── README.md
```

## Cara Pakai

1. **Letakkan FB Lite APK** di folder `fblite/` dengan nama `Facebook_Lite.apk`
2. **Push ke GitHub**
3. **GitHub Actions otomatis:**
   - Decompile FB Lite
   - Inject semua Spectrum (DEX + resources + manifest)
   - Recompile jadi 1 APK
   - Sign
   - Upload ke Releases
4. **Download** `FBLitePro-vXXXX.apk` dari tab Releases
5. **Install ke HP**

## Setelah Install

1. Aktifkan: **Pengaturan → Aksesibilitas → Lite Pro**
2. Buka FB Lite → floating Spectrum muncul otomatis
3. Login FB → cookies real bisa diekstrak langsung (satu proses)
4. Klik 👤 → upload foto profil klik otomatis seperti manusia
5. Long press 💾 → ekstrak & simpan cookies ke CSV

## Kenapa Harus 1 APK?

- **Cookie extraction** = harus satu proses dengan FB Lite agar `CookieManager` bisa diakses
- **Upload foto profil** = Accessibility Service dalam APK yang sama bisa klik UI FB Lite langsung
- **APK terpisah** = semua fitur di atas tidak akan bekerja
