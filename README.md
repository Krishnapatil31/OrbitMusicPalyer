# Orbit Music Player — Android Kotlin

**Version:** 1.1.0 (versionCode 2)

Android app starter combining Spotify App Remote controls with local music playback.

## Included
- Spotify App Remote connect, playlist URL/URI playback request, track metadata, previous/next/play-pause.
- Local audio discovery through MediaStore, A–Z sorting, search, and playback using Media3.
- Direct HTTP(S) audio download for audio the user owns or is legally permitted to save.
- GitHub Actions workflow to build a debug APK.
- Basic repository and app security hardening.

## Important limitations
- This does not remove Spotify advertisements, unlock Premium, or download Spotify tracks.
- Spotify playback is controlled through the official Spotify app.
- Background playback/lock-screen controls are not implemented in this starter.
- The official Spotify App Remote AAR and your Spotify Client ID are still required before Spotify features can work.
- No APK has been built or tested by this commit yet.

## Configure Spotify
1. Create an app in the Spotify Developer Dashboard.
2. Copy its Client ID into `spotifyClientId` in `app/src/main/java/com/example/orbitmusic/MainActivity.kt`.
3. Register redirect URI exactly as `orbitmusic://callback`.
4. Register package name `com.example.orbitmusic` and the signing SHA-1 if Spotify requests it.
5. Never embed a Spotify client secret or access token in the app.

## Add the official Spotify App Remote AAR securely
Do not commit the AAR to this public repository. Obtain it only from Spotify's official Android SDK documentation/repository. Encode the file as Base64 locally and add it as a GitHub Actions repository secret named `SPOTIFY_APP_REMOTE_AAR_BASE64`.

The workflow expects the AAR file name `spotify-app-remote-release-0.8.0.aar`. The AAR must be legally obtained and its redistribution terms respected.

## Build APK with GitHub Actions
1. Open the **Actions** tab.
2. Choose **Build Orbit Music Player APK**.
3. Tap **Run workflow** on branch `main`.
4. When it succeeds, open the run and download the artifact named `OrbitMusicPlayer-v1.1.0-debug-apk`.
5. Extract the downloaded ZIP and install the APK on your Android phone.

A successful workflow run is required before an APK exists. GitHub-hosted runner usage and artifact retention limits apply.

## Security checklist
- Keep this repository private if you do not want the source code to be publicly readable.
- Enable two-factor authentication on GitHub.
- Protect `main`: require pull requests, block force pushes, and restrict who can push.
- Never publish Spotify tokens, client secrets, Android signing keys, or keystores.
- Review Actions permissions and third-party workflow changes before merging.
- Keep dependencies updated; Dependabot is configured for Gradle and GitHub Actions.
- The app disables Android backup and cleartext HTTP network traffic. The audio downloader still accepts HTTP links because some legal direct-download hosts do not offer HTTPS; prefer HTTPS links.

## Versioning
For every app update, increment `versionCode` and update `versionName` in `app/build.gradle.kts`. Example next release: versionName 1.2.0, versionCode 3.

## Known unfinished work
- Add Spotify AAR and Client ID.
- Add and test app signing for release APKs.
- Add persistent background playback and media notification controls.
- Run a real Android build and device test; the GitHub workflow has not been executed yet.
