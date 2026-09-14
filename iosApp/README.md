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

- Signing: set `TEAM_ID` in `Configuration/Config.xcconfig` (or a git-ignored `Config.local.xcconfig`).
- The debug build talks to `http://localhost:8080` (backend `docker-compose` on the same Mac).
- Requires Xcode 16+; the Kotlin/Native toolchain is downloaded by Gradle on first build.
