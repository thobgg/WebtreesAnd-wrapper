# WebtreesAnd

### [⬇ APK herunterladen (v1.2)](https://github.com/thobgg/WebtreesAnd/releases/latest/download/WebtreesAnd-1.2.apk)

[![Release](https://img.shields.io/github/v/release/thobgg/WebtreesAnd?label=Release&color=2E75B6)](https://github.com/thobgg/WebtreesAnd/releases/latest)
[![Lizenz](https://img.shields.io/badge/Lizenz-GPL--3.0-c8922a)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3ddc84)](#installation)

Signiert, rund 4,8 MB. Für die Installation außerhalb des Play Store muss in den
Android-Einstellungen einmalig „Installation aus unbekannten Quellen" erlaubt werden.

---

Ein Android-Wrapper um eine [webtrees](https://webtrees.net/)-Instanz. Die App ist eine
schlanke Hülle: ein WebView, das die eigene webtrees-Adresse öffnet, damit die Instanz ein
Symbol im Launcher bekommt und die Anmeldung über Neustarts hinweg bestehen bleibt. Mehr
ist es nicht — und mehr soll es auch nicht sein.

Eine offizielle webtrees-App gibt es nicht. Der Name folgt der OsmAnd-Tradition:
OpenStreetMap → OsmAnd, webtrees → WebtreesAnd.

## Einrichtung

Beim ersten Start fragt die App nach der Adresse der eigenen webtrees-Installation. Sie
wird gespeichert und danach direkt geladen. Ein fehlendes `https://` wird ergänzt, ein
abschließender Schrägstrich entfernt. Lässt sich die gespeicherte Adresse nicht laden,
fragt die App erneut — so kommt man auch an einem Tippfehler wieder vorbei.

## Was die App tut

* Öffnet die hinterlegte webtrees-Instanz in einem WebView
* Hält Cookies und damit die Anmeldung über App-Neustarts hinweg
* Zurück-Taste navigiert in der WebView-Historie, statt die App zu schließen
* Downloads und PDFs werden an den externen Viewer weitergereicht
* `mailto:`- und `tel:`-Links gehen an die zuständige App
* Zoom aktiviert, Darstellung randlos unter Status- und Navigationsleiste
* Vollbild: fordert eine Seite es an (etwa eine Bildergalerie), gehen Status- und
  Navigationsleiste weg und kommen auf ein Wischen vom Rand zurück; die Zurück-Taste
  beendet dann das Vollbild statt in der Historie zu blättern

Es gibt keine Benutzerkonten, keine Analyse, keine Netzwerkzugriffe außer denen, die die
webtrees-Instanz selbst auslöst. Die einzige Berechtigung ist `INTERNET`.

## Installation

1. [APK herunterladen](https://github.com/thobgg/WebtreesAnd/releases/latest) — alle
   Fassungen liegen unter *Releases*, nicht im Dateibaum des Repos
2. Datei auf dem Gerät öffnen. Android fragt beim ersten Mal, ob die installierende App
   (Browser oder Dateimanager) unbekannte Apps installieren darf — einmalig erlauben
3. Nach der Installation beim ersten Start die Adresse der eigenen webtrees-Instanz
   eintragen

Mindestens Android 7.0 (API 24). Die App ist signiert; Updates lassen sich über eine
bestehende Installation einspielen, ohne sie vorher zu entfernen.

## Selbst bauen

```bash
git clone https://github.com/thobgg/WebtreesAnd.git
cd WebtreesAnd
./gradlew assembleDebug
```

`minSdk` 24, `compileSdk` 36, Kotlin, keine Abhängigkeiten außer AndroidX und Material.
Der Build braucht ein JDK 21 — mit neueren JDKs bricht das Android-Gradle-Plugin ab. Das
in Android Studio mitgelieferte JBR passt:

```bash
JAVA_HOME=/pfad/zu/android-studio/jbr ./gradlew assembleRelease
```

### Signierung

Der Release-Build liest die Keystore-Angaben aus `keystore.properties` im
Projektwurzelverzeichnis — diese Datei ist bewusst nicht eingecheckt. Fehlt sie, bleibt
der Release unsigniert, statt den Build abzubrechen:

```properties
storeFile=../mein-keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Lizenz

[GPL-3.0](LICENSE), wie die übrigen webtrees-Projekte hier.
