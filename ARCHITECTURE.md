# YPower Architecture

## 1. Stability-first split

```text
Target APK (/data/app)
        │
        ├──── no-hook controls ────> RootShell / RootService
        │                              ├─ DeviceIdle
        │                              ├─ AppOps
        │                              ├─ Standby
        │                              ├─ NetPolicy
        │                              └─ pm grant
        │
        └──── optional LSPosed ────> YPowerModule (target process only)
                                       ├─ identity virtualization
                                       ├─ app-side permission state
                                       └─ environment/API trace

YPower DiagnosticEngine
        ├─ package metadata
        ├─ logcat / exit-info
        ├─ /proc / mount / SELinux
        ├─ environment rules
        └─ CorrelationEngine
```

YPower intentionally does not install a default `system_server` hook.

## 2. Enhancement model

`AppProfile` is the per-package source of truth. The manager stores a local copy and mirrors enabled profiles to libxposed Remote Preferences. Dynamic LSPosed scope is requested when an app is enabled.

The Root layer can operate without LSPosed. The Hook layer is used only when the selected capability requires changing what the target app itself observes.

## 3. Diagnostics model

`DiagnosticEngine` creates `DiagnosticFinding` objects using four states: PASS / FAIL / WARN / UNKNOWN. Every report also retains raw evidence.

`CorrelationEngine` deliberately separates:

- environment presence (e.g. Root exists), and
- exit correlation (e.g. a root-sensitive query immediately precedes `abort`).

That distinction is required to avoid false claims about crash cause.

## 4. Diagnostic levels

### QUICK
Environment and system-side evidence with minimal target-process instrumentation.

### STANDARD
QUICK + Java sensitive-call trace and richer process state.

### DEEP
STANDARD + maps, loaded ELF/SO, mount namespace, FD, threads, meminfo and additional native-side evidence obtainable without a global framework hook.

Direct libc PLT/inline tracing can be added later through the reserved native tracing layer; it must remain target-process scoped and fail closed.

## 5. Permission semantics

`simulatePermissions` affects only permission checks performed inside the hooked target process. It does not alter the real PackageManager/PermissionManager grant state and does not turn signature/privileged/platform permissions into real grants.

This is intentional. Real privileged operations should be implemented through a narrow audited proxy API, rather than bypassing global permission checks.
