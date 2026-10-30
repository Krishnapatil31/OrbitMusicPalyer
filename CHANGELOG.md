# Changelog

## 1.1.0 (versionCode 2)
- GitHub Actions APK build workflow.
- Local music library, search, A–Z ordering, and playback.
- Spotify App Remote connection and playlist URI support.
- Legal direct audio URL download support.
- Security hardening: backups disabled, cleartext traffic disabled, least-privilege workflow permissions, and Dependabot configuration.
- Build workflow validates the checked-in Spotify SDK AAR and no longer overwrites it with a stale Base64 secret.
- Spotify connection now handles a missing Client ID safely.
