# Scalping Agent — Android App

Sleek Flutter client for the Scalping Agent backend. Connects to the FastAPI
server running on your PC over LAN or Tailscale, streams real-time P&L, lets
you fire the kill switch, and listens for push alerts via Firebase Cloud
Messaging.

## Get the APK

Three options, easiest first:

### 1. GitHub Actions (no local tooling needed)
Push to `main` (or the feature branch). The `Build APK` workflow produces
`app-release.apk` as a downloadable artifact. Transfer to phone and install.

### 2. Build locally
```bash
# one-time
flutter doctor              # fix anything red
cd mobile_app
flutter pub get
flutter create .            # scaffolds android/ ios/ if missing

# build
flutter build apk --release
# output: build/app/outputs/flutter-apk/app-release.apk
```

### 3. Cloud build
Open the repo in GitHub Codespaces with a Flutter devcontainer, or use
Codemagic free tier → `flutter build apk --release`.

## First-run setup

1. Start the backend on your PC: double-click `run_mobile_backend.bat` in the
   repo root. It listens on `http://<your-lan-ip>:8000`.
2. On the phone, open the app. Enter:
   - **Backend URL** — `http://<your-lan-ip>:8000` (LAN) or the Tailscale IP.
   - **Shared secret** — the value of `MOBILE_API_SHARED_SECRET` from `.env`.
3. Tap **Connect**. The dashboard will stream P&L in real time.

## Push notifications (optional)

1. Create a Firebase project → add an Android app with package
   `com.scalpingagent.app`.
2. Download `google-services.json` and drop it at
   `mobile_app/android/app/google-services.json` (after running `flutter create .`).
3. Download the Firebase Admin SDK service account JSON and place the path
   in `FIREBASE_CREDENTIALS` in your backend `.env`.
4. Restart the backend. Kill-switch / fill / daily-loss events will push to
   your phone.

## Project layout

```
mobile_app/
├── pubspec.yaml
├── analysis_options.yaml
└── lib/
    ├── main.dart                         # Entry + theme
    ├── api/
    │   ├── client.dart                   # Dio REST client
    │   ├── ws_stream.dart                # Reconnecting WebSocket
    │   └── models.dart                   # DTOs
    ├── state/
    │   ├── auth.dart                     # JWT persistence
    │   └── agent_state.dart              # Riverpod controller
    ├── fcm/fcm_service.dart              # Firebase init
    ├── widgets/kill_switch_button.dart   # Hold-to-trigger button
    └── screens/
        ├── login.dart                    # Server URL + secret
        ├── shell.dart                    # Bottom nav
        ├── dashboard.dart                # P&L + sparkline + kill switch
        ├── positions.dart                # Open positions
        ├── trade_feed.dart               # Signals/fills stream
        └── settings.dart                 # Risk config sliders
```
