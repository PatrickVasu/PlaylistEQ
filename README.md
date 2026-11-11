# PlaylistEQ – Equalizer pro App und Playlist (Android)

Ein systemweiter Equalizer für Android 9+, bei dem du **8, 16 oder 32 Bänder** wählen kannst und
dessen Einstellungen **automatisch je nach App und Playlist** wechseln.

## Funktionen

- **EQ-Profile** mit 8 / 16 / 32 Bändern (31 Hz – 16 kHz, ±12 dB), Vorverstärkung, Limiter gegen Übersteuern.
  Bandanzahl jederzeit umschaltbar, die Kurve wird dabei übernommen. Vorlagen: Neutral, Bass-Boost, Höhen, Stimme, Loudness.
- **Regeln**: App + Bedingung → Profil. Bedingung = Playlist, Album, Künstler, Titel oder „Ganze App“,
  exakt oder als Teiltreffer. Spotify-Playlist-Links können direkt eingefügt werden.
- **Reihenfolge**: Playlist-/Album-/…-Regel → „Ganze App“-Regel → Standardprofil → aus.
- Tab **„Jetzt“** zeigt live, was erkannt wird, und legt mit einem Tipp eine Regel für die laufende Playlist an.
- **Spotify-Verbindung (optional)** über die offizielle Web API, um den echten Playlist-Namen zu bekommen.
- **Globaler Modus** für Apps ohne eigene Audio-Session (YouTube, YouTube Music).
- Der Equalizer-Knopf in Spotify (Einstellungen → Wiedergabe → Equalizer) öffnet direkt PlaylistEQ.

## APK bauen

**Variante A – ohne eigene Software (GitHub):**
1. Neues (gern privates) Repository auf github.com anlegen und den Inhalt dieses Ordners hochladen.
2. Unter **Actions** startet automatisch „APK bauen“ (oder manuell per „Run workflow“).
3. Nach ca. 5 Minuten unten beim Lauf unter **Artifacts** „PlaylistEQ-apk“ herunterladen, entpacken,
   die APK aufs Handy kopieren und installieren („Installation aus unbekannten Quellen“ erlauben).

**Variante B – Android Studio:**
Ordner öffnen → warten bis Gradle synchronisiert hat → Handy per USB verbinden → ▶ Run.

## Einrichtung auf dem Handy

1. App öffnen → **„Zugriff erteilen“** (Benachrichtigungszugriff). Damit sieht die App, welche App gerade
   was abspielt. Benachrichtigungen selbst werden nicht gelesen.
2. Benachrichtigung erlauben (für den Hintergrunddienst).
3. In Spotify: *Einstellungen → Wiedergabe → Equalizer* einmal antippen und PlaylistEQ wählen.
4. Profile anlegen, dann Playlist abspielen → Tab „Jetzt“ → „diese Playlist“ → Profil wählen.
5. Für YouTube / YouTube Music: in den Einstellungen den **Globalen Modus** einschalten.
6. Optional: Spotify verbinden (Anleitung steht in der App unter Einstellungen).

## Wichtig zu wissen (technische Grenzen von Android)

- **Audio-Sessions:** Pro-App-EQ funktioniert nur, wenn die App ihre Audio-Session meldet. Spotify und die meisten
  lokalen Player (VLC, Poweramp, Musicolet …) tun das. YouTube und YouTube Music **nicht** – dafür gibt es den
  Globalen Modus (hängt sich an die Gesamtausgabe; wird von den meisten, aber nicht allen Geräten unterstützt).
  Im Globalen Modus wirkt immer das Profil der *gerade spielenden* App.
- **Playlist-Erkennung:**
  - *Spotify:* mit Spotify-Verbindung zuverlässig (eigene und abonnierte Playlists). Spotifys eigene
    Mixe (z. B. „Daily Mix“) gibt die API teils nicht preis – dann den Playlist-Link als Regel verwenden.
    Für eine eigene Spotify-Developer-App verlangt Spotify inzwischen ggf. ein Premium-Konto.
  - *YouTube Music:* Android bekommt meist nur Titel/Künstler/Album, den Playlist-Namen nur manchmal.
    Wenn im Tab „Jetzt“ keine Playlist steht, mit Album/Künstler oder „Ganze App“ arbeiten.
  - *Lokale Player:* je nach Player Playlist- bzw. Ordnername, Album immer.
- Samsung/Xiaomi u. ä. beenden Hintergrund-Apps aggressiv: Akku-Optimierung für PlaylistEQ auf
  „Nicht eingeschränkt“ stellen.
- Andere EQ-Apps (Wavelet, Samsung „Dolby Atmos“-EQ, Poweramp-EQ …) besser ausschalten, sonst doppelt sich der Effekt.

## Aufbau des Codes

```
app/src/main/java/de/playlisteq/app/
├── audio/EqEngine.kt            DynamicsProcessing-EQ je Audio-Session (8/16/32 Bänder + Limiter)
├── audio/AudioSessionReceiver   empfängt "Session geöffnet/geschlossen" der Musik-Apps
├── media/MediaListenerService   liest aktive Media-Sessions (App, Titel, Album, Playlist)
├── spotify/SpotifyClient.kt     Spotify Web API (PKCE-Login, aktuelle Playlist)
├── core/EqCoordinator.kt        Regel-Auswertung: welches Profil gilt wo
├── core/Store.kt                Speicherung von Profilen, Regeln, Einstellungen
├── service/EqService.kt         Vordergrunddienst, hält den EQ im Hintergrund aktiv
└── ui/                          Oberfläche (Jetpack Compose)
```
