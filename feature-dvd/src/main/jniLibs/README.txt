Missing Native Libraries / Fehlende Native Bibliotheken
=====================================================

To play DVD Video, this project requires pre-built native libraries for Android.
Um Video-DVDs abzuspielen, benötigt dieses Projekt vorkompilierte native Bibliotheken für Android.

Required Files / Benötigte Dateien:
-----------------------------------
- libdvdread.so
- libdvdnav.so
- libdvdcss.so (optional, for encrypted DVDs / für verschlüsselte DVDs)
- libffmpeg.so (optional, for decoding / für Dekodierung)

Locations / Speicherorte:
-------------------------
Place the .so files in the corresponding directory for each architecture:
Platzieren Sie die .so Dateien in das entsprechende Verzeichnis für jede Architektur:

- ARM64 (Modern Android Phones):
  feature-usb/src/main/jniLibs/arm64-v8a/

- ARM 32-bit (Older Phones):
  feature-usb/src/main/jniLibs/armeabi-v7a/

- x86_64 (Emulator):
  feature-usb/src/main/jniLibs/x86_64/

Important Note on Android 15+ (16 KB Page Size):
------------------------------------------------
If you target Android 15+, ensure these pre-built libraries are aligned to 16 KB page boundaries.
Wenn Sie Android 15+ unterstützen, stellen Sie sicher, dass diese Bibliotheken auf 16 KB Seitengrenzen ausgerichtet sind.
Use 'zipalign -p -P 16 4 <lib.so> <lib_aligned.so>' if needed, or ensure the source build uses '-Wl,-z,max-page-size=16384'.

Instructions / Anleitung:
-------------------------
1. Obtain the pre-built .so files for Android (e.g. from a VLC for Android build or similar project).
   Besorgen Sie sich die vorkompilierten .so Dateien für Android (z.B. aus einem VLC für Android Build).

2. Copy them into the folders mentioned above.
   Kopieren Sie diese in die oben genannten Ordner.

3. Rebuild the project.
   Projekt neu bauen.

Note: CMake will automatically find and link these libraries if they are present in these folders.
Hinweis: CMake findet und verknüpft diese Bibliotheken automatisch, wenn sie in diesen Ordnern vorhanden sind.
