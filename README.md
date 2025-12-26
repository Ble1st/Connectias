# Connectias

FOSS-Android-App für Netzwerk-Analyse, Sicherheit, Privacy und System-Utilities ohne Google-Abhängigkeiten. Fokus: lokale Verarbeitung, modulare Architektur und optionale DVD-Wiedergabe (LibVLC) inkl. USB-Blockdevice-Unterstützung.

## Überblick
- Security: RASP-Monitoring, Zertifikats- und Passwort-Checks, Verschlüsselungstools, Firewall-/Risiko-Analysen.
- Network: Port-Scanner, DNS-Tools, Traffic-/WiFi-Analyse.
- Privacy: Tracker-Erkennung, Berechtigungs- und Clipboard-Analyse.
- Device/Utilities: Geräteinfos, Batterie-/Storage-/Sensor-Monitor, Hash/Encoding-Tools, QR-Codes, API-Tester, Log Viewer.
- DVD: LibVLC-basierte DVD-Wiedergabe mit nativen libdvd*-Bibliotheken.

## Module
- Kern: `:core`, `:common`, `:feature-security`.
- Optional (per `gradle.properties` aktivierbar): `:feature-network`, `:feature-privacy`, `:feature-device-info`, `:feature-utilities`, `:feature-dvd`, `:feature-reporting`, `:feature-secure-notes`, `:feature-settings`, `:feature-usb`.

## Build (Kurz)
- Voraussetzungen: Android Studio Hedgehog+, JDK 17, Android SDK 33+ (minSdk 33, targetSdk 36).
- Debug-Build: `./gradlew assembleDebug`
- Tests: `./gradlew test` bzw. `./gradlew connectedAndroidTest`
- Module toggeln: `gradle.properties` (`feature.<name>.enabled=true/false`).

## Lizenzübersicht (Third-Party)
| Komponente                                                                                  | Version                           | Lizenz                           | Quelle/Link                                       | Lizenzdatei/Hinweis                                    |
|---------------------------------------------------------------------------------------------|-----------------------------------|----------------------------------|---------------------------------------------------|--------------------------------------------------------|
| LibVLC                                                                                      | 3.6.4                             | LGPL-2.1+ (einige Module GPL)    | https://www.videolan.org/vlc/libvlc.html          | AAR-META-INF (Videolan); dynamisch geladen             |
| libdvdcss                                                                                   | Quelle im Repo                    | GPL-2.0+                         | https://code.videolan.org/videolan/libdvdcss      | `feature-dvd/src/main/cpp/external/libdvdcss/COPYING`  |
| libdvdnav                                                                                   | Quelle im Repo                    | GPL-2.0+                         | https://code.videolan.org/videolan/libdvdnav      | `feature-dvd/src/main/cpp/external/libdvdnav/COPYING`  |
| libdvdread                                                                                  | Quelle im Repo                    | GPL-2.0+                         | https://code.videolan.org/videolan/libdvdread     | `feature-dvd/src/main/cpp/external/libdvdread/COPYING` |
| ZXing                                                                                       | 3.5.4                             | Apache-2.0                       | https://github.com/zxing/zxing                    | AAR-META-INF/NOTICE                                    |
| BouncyCastle                                                                                | 1.78.1                            | MIT-like (Bouncy Castle License) | https://www.bouncycastle.org/latest_releases.html | AAR-META-INF/LICENSE                                   |
| dnsjava                                                                                     | 3.6.3                             | BSD-2-Clause                     | https://github.com/dnsjava/dnsjava                | AAR-META-INF/LICENSE                                   |
| OkHttp                                                                                      | 4.12.0                            | Apache-2.0                       | https://square.github.io/okhttp                   | AAR-META-INF/NOTICE                                    |
| iText 7 Core                                                                                | 9.4.0                             | AGPL-3.0                         | https://itextpdf.com/                             | AAR-META-INF/LICENSE                                   |
| MPAndroidChart                                                                              | 3.1.0                             | Apache-2.0                       | https://github.com/PhilJay/MPAndroidChart         | AAR-META-INF/NOTICE                                    |
| SQLCipher                                                                                   | (per Dependency)                  | BSD-ähnlich                      | https://www.zetetic.net/sqlcipher/                | AAR-META-INF/LICENSE                                   |
| Timber                                                                                      | 5.0.1                             | Apache-2.0                       | https://github.com/JakeWharton/timber             | AAR-META-INF/NOTICE                                    |
| libaums                                                                                     | 0.10.0                            | Apache-2.0                       | https://github.com/magnusja/libaums               | AAR-META-INF/NOTICE                                    |
| RootBeer                                                                                    | 0.1.1                             | Apache-2.0                       | https://github.com/scottyab/rootbeer              | AAR-META-INF/LICENSE                                   |
| AndroidX/Jetpack (Material, Compose, Navigation, Room, WorkManager, Coroutines, Hilt, etc.) | siehe `gradle/libs.versions.toml` | Apache-2.0                       | https://developer.android.com/jetpack             | AAR-META-INF/NOTICE                                    |

Hinweis zu GPL-Komponenten (libdvd*):
- Quellcode liegt im Repo unter `feature-dvd/src/main/cpp/external/`.
- Statisches oder gemeinsames Linking mit GPL-Bibliotheken kann Copyleft-Anforderungen für das Gesamt-Binary auslösen. Bitte Lizenz-Compliance sicherstellen (Quelloffenlegung, Hinweistext, ggf. dynamisches Laden).

## Ressourcen
- LibVLC-Integration: `docs/VLC_INTEGRATION.md`
- Lizenzdateien: siehe Tabelle / Unterordner `feature-dvd/src/main/cpp/external/`
- Modul- und Build-Hinweise: `gradle.properties`, `build.gradle.kts` (Projekt & Module)

## Contributing
- Pull Requests willkommen. Bitte Coding-Guidelines und Sicherheitsanforderungen beachten; sensible Daten niemals in Logs oder Repos ablegen.

## Changelog
- Siehe Projekt-History (Git); eigenständige Changelog-Datei kann ergänzt werden.

