# Waktu Solat

Aplikasi Android native (Kotlin + Jetpack Compose) untuk jadwal solat, lokasi GPS, countdown waktu berikutnya, notifikasi, bacaan solat Arab/latin/arti, dan cache offline.

## Fitur
- Jadwal Subuh, Terbit, Zuhur, Asar, Magrib, dan Isya dari AlAdhan API.
- Koordinat dari lokasi terakhir GPS/network yang diizinkan; fallback awal Jabung, Jawa Timur.
- Sinkronisasi manual dan pekerjaan latar belakang berkala setiap 24 jam (WorkManager).
- Countdown menuju waktu solat berikutnya.
- Notifikasi saat masuk waktu solat. Tombol pengaturan suara membuka kanal notifikasi Android agar pengguna dapat memilih nada/audio adzan yang tersedia di perangkat.
- Bacaan solat Arab, latin, dan arti Bahasa Indonesia.
- Jadwal terakhir disimpan lokal dan tetap tampil tanpa internet.

## Perhitungan
Default menggunakan metode AlAdhan `method=20` (Singapore, sudut Subuh 20° dan Isya 18°). Hasil dapat berbeda beberapa menit dari jadwal resmi Kementerian Agama atau masjid setempat. Cocokkan pengaturan/metode dengan otoritas lokal sebelum dijadikan acuan resmi.

## Build APK lewat GitHub Actions
1. Buat repository GitHub dan upload seluruh isi folder project ini.
2. Push ke branch `main` atau `master`, atau buka tab **Actions** lalu jalankan workflow **Build Android APK** dengan **Run workflow**.
3. Buka hasil run yang selesai dan unduh artifact `WaktuSolat-debug-apk`.
4. Ekstrak artifact, lalu instal `app-debug.apk` di Android. Izinkan notifikasi dan akses lokasi saat diminta.

## Build lokal
Butuh JDK 17, Android SDK Platform 35, Build Tools 35.0.0, dan Gradle 8.9.

```bash
gradle --no-daemon assembleDebug
```

APK berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan
- Aplikasi memerlukan internet untuk sinkronisasi jadwal. Cache jadwal terakhir tersedia offline.
- Android dapat menunda pekerjaan latar belakang sesuai optimasi baterai pabrikan. Buka aplikasi dan lakukan sinkronisasi manual jika jadwal belum diperbarui.
- Aplikasi tidak membundel rekaman adzan; suara diatur melalui kanal notifikasi Android.
- Lokasi otomatis memakai lokasi terakhir yang tersedia dari GPS/network; jika belum ada fix lokasi, aplikasi memakai koordinat fallback Jabung, Jawa Timur.
