# iosApp – thin Xcode wrapper

The Xcode project is **generated**, not committed.

```sh
brew install xcodegen
cd iosApp
xcodegen generate          # creates iosApp.xcodeproj from project.yml
open iosApp.xcodeproj
```

Select the `iosApp` scheme and an iOS 16+ simulator, then Run. The "Compile Kotlin Framework"
build phase calls `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`, which builds the
`ComposeApp` static framework for the selected SDK/arch and puts it where Xcode expects it.

- Signing: `TEAM_ID` lives in the git-ignored `Configuration/Config.local.xcconfig` (`make ios-team` writes it).
- The debug build talks to `http://localhost:18473` (`make run` in `finance-backend` on the same Mac).
- Requires Xcode 26+ (Kotlin 2.3 / Compose 1.11 link against the iOS 26 SDK); the Kotlin/Native toolchain is downloaded by Gradle on first build.

## Real iPhone (free Apple ID, release build)

Plan: `../../docs/DEPLOY-PLAN.md`. One-time setup:

1. Xcode → Settings → Accounts → add your Apple ID (creates the free "Personal Team").
2. iPhone: Settings → Privacy & Security → **Developer Mode** on (restart), plug in, unlock, tap *Trust*.
3. `make ios-team` — detects the Personal Team ID and writes `Config.local.xcconfig`
   (`make ios-team TEAM=XXXXXXXXXX` if detection fails; the ID is shown in Xcode → Settings → Accounts).
4. `make prod-url URL=https://<domain>` — the production server (API + Keycloak on one host).

Then `make ios-device`: release build → install → launch. First launch only: Settings → General →
VPN & Device Management → trust your Apple ID.

A free-account install **expires after 7 days**. Run `make ios-device` again before that; installing over the
existing app keeps its data. If the bundle ID `com.uit.finance` is already taken on Apple's side, set
`BUNDLE_ID=com.uit.finance.<something>` in `Config.local.xcconfig` (the login redirect scheme does not change).
