# BTC Wall

A live wallpaper for Android that draws a Bitcoin price chart (plus ETH and LINK)
straight onto your home screen, refreshing every 5 minutes from Kraken's public API.

A little roller coaster rider sits on the line and tilts with the trend.

## Features

- BTC price chart for a selectable time frame: 24H, 7D, 1M, 3M, 1Y, or ALL
- ETH and LINK prices in the corner (each can be shown or hidden)
- Green when the selected coin is up, red when it's down
- Optional "trend line only" mode: draws a single roller-coaster rail from the
  start of the period to now, instead of the full wavy price line
- Adjustable "car location" slider for where the rider sits along the chart
- Optional custom background photo instead of plain black
- No account, no API key, no ads, no network access beyond the public price API

## Art

The rider illustration and the app icon were generated with AI tools by the
repo owner, not sourced from a third party.

## Building

Requires Android Studio (or the command-line Gradle wrapper) and the Android
SDK. Open the project folder in Android Studio and build the APK with
**Build > Generate App Bundles or APKs > Generate APKs**, or from the
command line:

```
./gradlew assembleDebug
```

The APK will be under `app/build/outputs/apk/debug/`.

## Installing

Sideload the APK (enable "Install unknown apps" for whichever app you use to
open it), then open **BTC Wall** and tap **Set as wallpaper**.

## License

MIT — see [LICENSE](LICENSE).
