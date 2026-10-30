# Orbit Music Player — Android Kotlin

**Version:** 1.1.0 (versionCode 2)

Android app starter combining Spotify App Remote controls with local music playback.

## Included
- Spotify App Remote connect, playlist URL/URI playback request, track metadata, previous/next/play-pause.
- Local audio discovery through MediaStore, A–Z sorting, search, and playback using Media3.
- Direct HTTPS audio download for audio the user owns or is legally permitted to save.
- GitHub Actions workflow to build a debug APK.
- Basic repository and app security hardening.

## Important limitations
- This does not remove Spotify advertisements, unlock Premium, or download Spotify tracks.
- Spotify playback is controlled through the official Spotify app.
- Background playback/lock-screen controls are not implemented in this starter.
- The Spotify App Remote AAR is included in `app/libs`; a valid `SPOTIFY_CLIENT_ID` Actions secret is required for Spotify features.
- A successful GitHub Actions build is required before installing an APK. Real-device testing is still required.

## Configure Spotify
1. Create an app in the Spotify Developer Dashboard.
2. Add the Client ID as a GitHub Actions repository secret named `SPOTIFY_CLIENT_ID` (Settings → Secrets and variables → Actions).
3. Register redirect URI exactly as `orbitmusic://callback`.
4. Register package name `com.example.orbitmusic` and the signing SHA-1 if Spotify requests it.
5. Never embed a Spotify client secret or access token in the app.

## Spotify App Remote SDK dependency
The official SDK AAR is stored at `app/libs/spotify-app-remote-release-0.8.0.aar` and is validated by the build workflow. The old `SPOTIFY_APP_REMOTE_AAR_BASE64` secret is only a fallback if that file is absent; it should not overwrite the checked-in AAR. Obtain SDK files from Spotify's official Android SDK distribution and follow its license terms.

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
- The app disables Android backup and cleartext HTTP network traffic. The audio downloader accepts HTTPS links only.

## Versioning
For every app update, increment `versionCode` and update `versionName` in `app/build.gradle.kts`. Example next release: versionName 1.2.0, versionCode 3.

## Known unfinished work
- Confirm the registered Spotify Android package name and stable signing fingerprint match the installed build.
- Add and test app signing for release APKs.
- Add persistent background playback and media notification controls.
- Run a real Android build and device test; the GitHub workflow has not been executed yet.
