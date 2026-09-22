# Changelog

## Unreleased

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
