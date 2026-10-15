# ValoTrak — Valorant Progress Tracker (Android)

A small Jetpack Compose Android app that tracks a Valorant account's progress using **free community APIs**:

- **HenrikDev API** (requires a free key) → account level, current rank, RR, ELO, last-match RR change, peak rank, recent competitive games
- **Valorant-API (valorant-api.com)** → player card banners and map splash art (no key required)

## Features (v1)
- Enter a RIOT ID (`Name#TAG`) + region
- Save your free HenrikDev API key locally (SharedPreferences)
- Current rank card with rank icon, RR, ELO, last-match RR change, peak rank
- Account level + player card banner header
- Recent competitive match history with map art and RR changes
- Valorant-style dark UI

## Requirements
- Android Studio (latest stable)
- Android SDK 24+ (target 37)
- A free HenrikDev API key → https://docs.henrikdev.xyz/general/auth
- Internet connection

## Getting started
1. Clone the repo:
   ```bash
   git clone <your-repo-url>
   ```
2. Open the project in Android Studio and let Gradle sync.
3. Run on an emulator or device.
4. On first launch, paste your HenrikDev API key, enter your RIOT ID (e.g. `Player#TAG`) and region (`eu`, `na`, `ap`, etc.), then tap **Fetch**.

## Notes
- The app runs with software rendering enabled for maximum emulator compatibility.
- HenrikDev requires an API key since v4.0 (free, no wait) — get one via their Discord `#get-a-key` channel (`VALORANT (Basic Key)`).
- No secrets are committed; the key is stored only on your device.

## Tech stack
- Kotlin + Jetpack Compose (Material3)
- `HttpURLConnection` + `org.json` (no third-party HTTP/image libs)
- Kotlin coroutines (Dispatchers.IO)

## Roadmap
- Top agents / KDA per match
- Battle pass / account XP progress
- Offline caching
- Charts for RR/ELO history
- Rank-up notifications

## License
Personal project. Valorant assets are property of Riot Games; used under Riot's fan content/asset policies.