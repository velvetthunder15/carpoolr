# Carpoolr

Android app (Kotlin, Jetpack Compose) that pools students on overlapping routes and splits the fare.

## UI shell
- Top bar: menu (left), `carpoolr` tag in Anton (centre), profile (right). All three are liquid glass.
- Bottom: floating liquid-glass icon bar (Home, Find, Rides, Chats). You can drag the pill.
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
