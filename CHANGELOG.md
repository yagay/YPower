# Changelog

## Unreleased

- Switched the Diagnostic Center to runtime-session diagnostics.
- Diagnostic results now contain only checks/events actually observed while the target app runs.
- Removed static device-environment PASS/FAIL rows from app diagnostic results.
- Added Start diagnosis -> Launch target app -> Finish & analyze workflow.
- Diagnostic sessions temporarily enable trace providers, then restore the app's original YPower configuration.
- YPower force-stop operations are outside the measured window and cannot be misclassified as target-app exits.
- Added exact package and timestamp tagging to YPowerTrace events.
- Added a DETECTED result state for observed checks; absent checks are not displayed.
- Runtime exit correlation now uses events from the same diagnostic session only.
- Added Recommended apps screen with installed-app filtering.
- Added built-in recommendation presets for Douyin, Douyin Lite, Hongguo and Hongguo overseas.
- Recommended presets show suggested Hook groups and apply them in one tap.
- One-tap recommendation enables YPower, writes the preset, applies Root enhancements and requests LSPosed dynamic scope.
- Fixed app-detail enable flow so enabling an app also requests LSPosed scope.
- Recommended presets avoid identity/permission spoofing by default and favor trace-only diagnostics.

- Split LSPosed into six independently controlled providers: Identity, Permission, PackageScan, FileTrace, CommandTrace and PropertyTrace.
- Added ApplicationPackageManager identity flag compatibility.
- Added PackageManager environment/package enumeration tracing.
- Added FileInputStream/RandomAccessFile sensitive path tracing.
- Added all Runtime.exec overloads, ProcessBuilder and active-exit tracing.
- Added SystemProperties/System.getProperty tracing for boot/integrity/environment checks.
- Added compact call stacks to sensitive YPowerTrace events.
- Migrates the old aggregate Java/environment trace switch into the new per-provider switches.

## 0.1.0

- Initial YPower implementation.
- Root enhancement profiles and boot restore.
- Dynamic libxposed scope and remote preferences.
- Target-process system identity / app-side permission simulation.
- Java environment and active-exit tracing.
- Quick / Standard / Deep diagnostics.
- Simple / Detailed / Raw report views and JSON export.
- Root/Hook/mount/SELinux/integrity/crash/resource/network rule coverage.
- GitHub Actions Android build workflow.
