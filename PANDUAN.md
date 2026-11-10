# PANDUAN — Noname Launcher (Termux + GitHub Actions)

Panduan lengkap dari ZIP ini sampai APK ter-install di HP.
Semua perintah diketik di **Termux**. Ikuti urut dari atas, jangan ada yang dilewati.

> Kamu sudah selesai setup dasar (git, gh, micro, openjdk-17, unzip, storage, login GitHub).
> Folder project sudah dibuat: `~/projects/noname`.

---

## Langkah 1 — Ekstrak ZIP ini ke folder project

Unduh file `noname.zip` dari chat ke HP (masuk ke folder **Download**), lalu di Termux:

```bash
cd ~/projects
unzip -o ~/storage/downloads/noname.zip
cd noname
ls
```

Hasil `ls` harus menampilkan: `app  gradle  gradlew  PANDUAN.md  README.md  settings.gradle.kts` dan lainnya.

> Jika zip kamu simpan di lokasi lain, sesuaikan path-nya.

---

## Langkah 2 — Buat keystore (kunci tanda tangan APK)

Keystore ini **identitas permanen app-mu**. Buat SEKALI saja dan **JANGAN hilangkan**.

```bash
keytool -genkeypair -v -keystore ~/noname.keystore -alias noname -keyalg RSA -keysize 2048 -validity 10950
```

Jawab pertanyaannya:

1. `Enter keystore password:` → ketik password pilihanmu (**min. 6 karakter, campur huruf+angka, TANPA simbol** biar aman di-tempel nanti) → Enter
2. `Re-enter new password:` → ketik sama persis → Enter
3. Nama & organisasi (`CN`, `OU`, `O`...) → **boleh langsung Enter semua**
4. `Is CN=Unknown ... correct?` → ketik `ya` (atau `yes`) → Enter
5. `Enter key password for <noname> (RETURN if same as keystore password):` → **langsung Enter saja** (sama dengan password di atas)

Lalu ubah ke format base64 untuk GitHub Secrets:

```bash
base64 -w0 ~/noname.keystore > ~/noname_keystore.b64
wc -c ~/noname_keystore.b64
```

⚠️ **BACKUP KEYSOTORE SEKARANG** — salin ke storage HP (dan nanti ke cloud/Google Drive):

```bash
cp ~/noname.keystore ~/storage/downloads/noname.keystore.backup
```

> Kehilangan keystore = APK baru tidak bisa dipasang di atas APK lama
> (harus uninstall dulu, dan sebagai launcher itu merepotkan).

---

## Langkah 3 — Buat repo GitHub + push pertama

Masih di dalam folder `~/projects/noname`:

```bash
git init -b main
git add -A
git commit -m "Noname v1: launcher dasar"
gh repo create noname --public --source=. --push
```

Perintah terakhir: membuat repo `erkfley/noname` (public), memasang remote, dan mendorong kode.
Begitu selesai, **GitHub Actions langsung mulai build pertama** (± 5–10 menit).

> Build pertama akan menghasilkan APK **debug** (belum ter-sign) karena secrets
> belum diisi — itu normal, lanjut ke Langkah 4.

Cek status build kapan saja:

```bash
gh run list
gh run watch        # ikuti build terbaru secara langsung (Ctrl+C untuk keluar)
gh run web          # buka di browser kalau mau lihat tampilan web
```

---

## Langkah 4 — Isi GitHub Secrets (agar APK ter-sign permanen)

```bash
gh secret set KEYSTORE_BASE64 < ~/noname_keystore.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS -b"noname"
gh secret set KEY_PASSWORD
```

- `KEYSTORE_BASE64` → otomatis dari file (tidak ada prompt)
- `KEYSTORE_PASSWORD` → muncul tulisan *“Paste your secret”* → **tempel password keystore-mu** → Enter
- `KEY_ALIAS` → diisi langsung `noname`
- `KEY_PASSWORD` → tempel password yang sama → Enter

Memicu ulang build supaya hasilnya ter-sign:

```bash
git commit --allow-empty -m "trigger build signed" && git push
```

Setelah selesai, APK-nya bernama `Noname-v<nomor>-signed.apk`.
(Kalau ternyata masih `-debug`, berarti salah satu secrets salah ketik — cek Langkah 8.)

---

## Langkah 5 — Download APK hasil build

```bash
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run download "$RUN_ID" -n Noname-APK -D ~/apk
cp ~/apk/*.apk ~/storage/downloads/
```

APK sekarang ada di folder **Download** HP-mu.

---

## Langkah 6 — Install & jadikan launcher default

1. Buka aplikasi **Files / Files by Google** → folder **Download** → tap `Noname-v....apk`
2. Kalau diminta izin: **“Install unknown apps”** → izinkan untuk aplikasi Files tersebut
3. Install → selesai
4. Tekan tombol **HOME** → Android menampilkan pilihan launcher → pilih **Noname** → **Always / Selalu**

Selesai! HP-mu sekarang memakai launcher buatanmu sendiri. 🎉

> Untuk kembali ke launcher bawaan: Settings → Apps → Default apps → Home app.

---

## Langkah 7 — Alur kerja harian (mengubah kode)

```bash
cd ~/projects/noname
micro app/src/main/java/com/erkfley/noname/Ui.kt   # edit kode (Ctrl+S simpan, Ctrl+Q keluar)
git add -A
git commit -m "deskripsi perubahan"
git push
# tunggu build selesai, lalu ulangi Langkah 5 untuk mengambil APK baru
```

Karena app sudah ter-sign dengan kunci yang sama, APK baru bisa langsung
**di-install di atas yang lama tanpa uninstall** (data/aturan tidak hilang).

### Shortcut download + install dalam 3 baris

```bash
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
gh run download "$RUN_ID" -n Noname-APK -D ~/apk && cp ~/apk/*.apk ~/storage/downloads/
```

### (Opsional) Lihat log crash langsung dari HP

```bash
pkg install -y android-tools
```
Aktifkan **Developer options → Wireless debugging** di HP, lalu pasangkan dari Termux:
```bash
adb pair 127.0.0.1:PORT   # port & kode pairing muncul di layar Wireless debugging
adb connect 127.0.0.1:PORT
adb logcat --pid=$(pidof com.erkfley.noname)
```

---

## Langkah 8 — Kalau build GAGAL (troubleshooting)

Lihat pesan errornya dulu:

```bash
gh run view --log-failed
```

| Gejala | Penyebab umum | Solusi |
|---|---|---|
| `Keystore ... or corrupted` / ukuran 0 byte | `KEYSTORE_BASE64` tidak ter-set benar | ulangi `gh secret set KEYSTORE_BASE64 < ~/noname_keystore.b64` |
| Build sukses tapi APK `-debug` | salah satu secrets kosong / salah nama | cek 4 secrets: `gh secret list`; nama harus persis |
| `jarsigner: unable to sign jar ... invalid` | password keystore ≠ `KEYSTORE_PASSWORD` | isi ulang secret dengan password yang benar |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK lama beda signature | uninstall dulu → pastikan memakai APK `-signed` |
| Error Kotlin/Compose saat compile | typo saat edit kode | salin log dari `gh run view --log-failed`, kirim ke GLM untuk dibedah |
| `gh repo create` bilang repo sudah ada | nama `noname` terpakai | pakai nama lain: `gh repo create noname-launcher --public --source=. --push` |

Salin-tempel pesan error ke chat GLM bila macet — dibedah bareng.

---

## Struktur project (yang perlu kamu tahu)

```
noname/
├── .github/workflows/build.yml     ← resep build otomatis di GitHub
├── PANDUAN.md                      ← file ini
├── settings.gradle.kts             ← daftar modul
├── build.gradle.kts                ← versi plugin (AGP/Kotlin)
├── gradle/ + gradlew               ← Gradle wrapper (jangan diubah)
└── app/
    ├── build.gradle.kts            ← pengaturan app (SDK, dependensi)
    └── src/main/
        ├── AndroidManifest.xml     ← "kartu identitas" app + intent HOME
        ├── java/com/erkfley/noname/
        │   ├── MainActivity.kt     ← pintu masuk app
        │   ├── Apps.kt             ← data aplikasi, wallpaper, aksi buka/hapus
        │   └── Ui.kt               ← tampilan: grid home, drawer, menu
        └── res/                    ← ikon, tema, nama app
```

## Rencana fase berikutnya

- Fase 2: dock, jam/tanggal di home, efek visual
- Fase 3: ukuran grid bisa diatur, tema warna, icon pack
- Fase 4: widget, folder, drag & drop, badge notifikasi

Diskusikan fitur berikutnya dengan GLM, ubah kodenya bareng, push, selesai.
