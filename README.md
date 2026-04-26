# Offline UPI Capture (Expo / React Native)

An "Offline Payment Capture" UI for UPI built with Expo. Users can capture UPI
payment intents (by scanning a QR or entering a UPI ID / mobile number) while
offline. Captured intents go into a local **Pending Offline Ledger** (backed by
`AsyncStorage`) and are visually surfaced on the dashboard. When the device
comes back online, a sync routine moves them into the **Audit / History**
ledger one by one with a simulated delay.

> This is a UI / queueing layer only. No real money is moved. The app simulates
> the sync behaviour for demo / capture purposes.

## Features

- Persistent global **network banner**: yellow/orange when offline, green when
  online. Live state via `@react-native-community/netinfo`.
- **Offline-aware theming**: when offline, the palette dims to give a clear
  visual cue that the app is operating in offline mode.
- **Dashboard** with large action buttons (`Scan QR`, `Enter Mobile / UPI ID`),
  a prominent `Pending Transactions` list, and a recent History preview.
- **Payment Entry** screen with local regex validation for UPI IDs and Indian
  mobile numbers, amount validation, and a `Save Offline` CTA.
- **QR Scanner** screen using `expo-camera`. Scanned `upi://pay?...` links are
  parsed locally and used to pre-fill the Payment Entry screen.
- **Offline success screen**: "Payment Intent Saved! This will be processed
  automatically when your internet returns."
- **Queue + sync logic**: `useEffect` listens to `NetInfo`. On an
  offline → online transition, it loops through the pending array, marks each
  item completed (with a 2 s simulated delay per item), and moves it into the
  audit / history list.
- **Audit / History** screen showing all completed payments.

## Project structure

```
App.js                          Navigation shell + provider wiring
index.js                        Expo entry
app.json                        Expo config (camera permission etc.)
eas.json                        EAS build profiles (preview = APK)
babel.config.js                 babel-preset-expo
src/
  components/
    NetworkBanner.js            Persistent online/offline banner
    PendingTransactionItem.js   Row used in pending + history lists
  context/
    NetworkContext.js           NetInfo listener, queue + sync state, theme
  screens/
    DashboardScreen.js
    PaymentEntryScreen.js
    QRScannerScreen.js
    SuccessScreen.js
    HistoryScreen.js
  theme/
    colors.js                   Online / offline palettes
  utils/
    storage.js                  AsyncStorage wrappers (pending + history)
    validation.js               UPI / mobile / amount regex
    upiParser.js                Parses upi:// QR payloads
    format.js                   Currency, timestamp, id helpers
```

## Where to get the APK

Every push to `claude/offline-upi-payment-app-WMuOl` triggers
`.github/workflows/build-apk.yml`, which:

1. Runs the Jest test suite (logic + UI render tests).
2. Runs `npx expo prebuild -p android` to generate the native Android project.
3. Builds a release APK with `./gradlew assembleRelease`.
4. Uploads the APK as a workflow artifact.
5. Publishes a pre-release on the repo with the APK attached
   (`apk-<branch>-<run-number>`).

Download options once a build finishes:

- **Releases tab**: <https://github.com/saimanojbaru/testrepo/releases> — pick
  the latest `Offline UPI Capture (build N)` and grab the `.apk`.
- **Actions artifact**: open the workflow run and download the
  `offline-upi-capture-<sha>.apk` artifact.

The APK is signed with the Android debug key (Expo's prebuild default), which
is fine for sideload testing but not for Play Store distribution.

## Build instructions

### 1. Initialize (only if starting from scratch elsewhere)

```bash
# Already scaffolded in this repo. To create a fresh app from zero:
npx create-expo-app offline-upi-capture --template blank
cd offline-upi-capture
```

### 2. Install dependencies

From the project root:

```bash
npm install
```

If you ever need to add the listed dependencies to a fresh project manually:

```bash
npx expo install \
  @react-native-community/netinfo \
  @react-native-async-storage/async-storage \
  expo-camera \
  expo-status-bar \
  react-native-safe-area-context \
  react-native-screens

npm install \
  @react-navigation/native \
  @react-navigation/native-stack
```

### 3. Run locally

```bash
npx expo start
# press "a" to open on an Android emulator / connected device
```

### 4. Build a preview APK with EAS

```bash
# one-time
npm install -g eas-cli
eas login

# from the project root
eas build -p android --profile preview
```

The `preview` profile in `eas.json` is configured to produce a distributable
`.apk`. EAS will print a download URL once the build completes.

## Notes on the sync simulation

The sync routine lives in `src/context/NetworkContext.js`. When `NetInfo`
reports a transition from offline → online, it pulls the pending queue from
`AsyncStorage`, waits 2 s per item (`SYNC_DELAY_MS`), then moves each item
into the history list one at a time. The currently-syncing item shows a
`SYNCING` badge in the dashboard.

## Validation rules

- **UPI ID:** `^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$`
- **Indian mobile:** `^[6-9]\d{9}$`
- **Amount:** numeric with up to 2 decimals, between ₹0.01 and ₹100,000.

These run entirely on-device — no backend lookup is needed.
