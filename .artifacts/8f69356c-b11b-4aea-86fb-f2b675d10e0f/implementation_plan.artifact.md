# Implementation Plan - Versioning & Basic Testing

Enhance the build system to support dynamic versioning from CI and add basic unit tests to ensure the CI pipeline is functional.

## User Review Required

> [!NOTE]
> **Versioning:** I will update the Gradle script to use properties passed from GitHub Actions. If no property is provided (e.g., local build), it will fallback to "1.0.0".
> **Testing:** I will add a basic unit test. Since most current logic depends on Android components (Broadcasts, DataStore, Services), I will add a utility class for SSID processing and test it to provide a valid target for the CI `testDebugUnitTest` task.

## Proposed Changes

### 1. Build System
#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/sebfo/AndroidStudioProjects/TailscaleSwitch/app/build.gradle.kts)
- Change `versionCode` and `versionName` to read from project properties:
    - `versionCode` from `-PversionCode` (fallback to 1).
    - `versionName` from `-PversionName` (fallback to "1.0.0").

### 2. Logic Refactoring
#### [NEW] [SsidUtils.kt](file:///C:/Users/sebfo/AndroidStudioProjects/TailscaleSwitch/app/src/main/java/dev/sfpixel/tailscaleswitch/util/SsidUtils.kt)
- Move SSID cleaning logic (removing quotes, handling unknown SSIDs) to a pure Kotlin utility function.

#### [MODIFY] [WifiMonitoringService.kt](file:///C:/Users/sebfo/AndroidStudioProjects/TailscaleSwitch/app/src/main/java/dev/sfpixel/tailscaleswitch/service/WifiMonitoringService.kt)
- Use the new `SsidUtils` function.

### 3. Testing
#### [NEW] [SsidUtilsTest.kt](file:///C:/Users/sebfo/AndroidStudioProjects/TailscaleSwitch/app/src/test/java/dev/sfpixel/tailscaleswitch/util/SsidUtilsTest.kt)
- Add unit tests for `SsidUtils.cleanSsid()` to verify it correctly handles quoted SSIDs and edge cases.

## Verification Plan

### Automated Tests
- Run `./gradlew testDebugUnitTest` locally to verify the new test passes.
- Run `./gradlew assembleDebug -PversionName=2.0.0 -PversionCode=2` and check if the build succeeds with these parameters.
