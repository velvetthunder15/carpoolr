# Carpoolr

Android app (Kotlin, Jetpack Compose) for instant pooling: people heading the same way at the same time get matched, and the cab fare is split by distance ridden.

## UI shell
- Top bar: raspberry route mark + `carpoolr` in Anton (centre, no glass), profile in liquid glass (right). The profile sheet holds all settings.
- Bottom: floating liquid-glass icon bar (home, rides, chats). You can drag the pill.
- UI copy is all lowercase; places the user types keep their own casing.
- Icons: Lucide (ISC), converted to vector drawables, 1.75 stroke.
- Fare split: `pool/FareSplit.kt`. The fare becomes a per-km rate, and each stretch is split among the riders still on board. Shares are whole rupees that sum to the fare. Unit tested in CI.
- Liquid glass comes from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) `backdrop` 1.0.6: blur, lens refraction and specular highlight. Full refraction runs on Android 13+, with a graceful blur on older versions.
- Motion uses spring physics everywhere, on render-thread `graphicsLayer` properties only, so it stays at 60fps+:
  - press-scale micro-interactions
  - staggered entrances
  - tab transitions
  - side sheets
  - a self-drawing route empty state

## Build
CI builds `app-debug.apk` and `app-release.apk` on every push (Actions → Build APK → artifact `carpoolr-apk`).
To build locally, run `./gradlew assembleDebug` (needs JDK 21 and the Android SDK).

## Brand
`brand/generate.py` makes every logo asset from one geometry:
- SVGs
- PNGs
- favicons with `site.webmanifest`
- Android adaptive icon vectors
