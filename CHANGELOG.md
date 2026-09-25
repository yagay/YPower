# Changelog

## Unreleased

- Added DuckDetector gap-analysis coverage for native system properties, kernel/proc identity, SELinux, memory/FD/smaps and linker/signal/vDSO inspection behavior.
- Added ByteHook observation for open/openat/opendir, __system_property_get, uname, SELinux xattrs, sigaction, getauxval(AT_SYSINFO_EHDR), executable mprotect and dl_iterate_phdr.
- Added stable Rule IDs for kernel, SELinux and memory inspection events; path/file checks remain CHECKED unless a concrete result proves a hit.
- Added opt-in raw syscall experiment using strace for deep diagnostics. It is disabled by default because ptrace can alter anti-debug behavior.
- Raw syscall traces are exported into the diagnostic artifact set as syscall-trace.txt and exposed in JSON/raw reports.

- Added an observe-only exception propagation layer for Java fatal, Kotlin coroutines and RxJava2/3.
- Added Thread.dispatchUncaughtException tracing without replacing the target app's UncaughtExceptionHandler.
- Added observation of default/thread exception handler installation and RxJava global error handlers.
- Exception events now record Throwable identity, class/message/cause/suppressed count, PID/TID/thread and Throwable stack.
- Runtime reports correlate Coroutine/RxJava events to Java uncaught by identical Throwable identity or same-TID time proximity.
- Security attribution can gain evidence from a detection→Java Fatal bridge, while error/instrumentation events remain excluded from primary security-cause selection.

- Added Deep-mode Perfetto and simpleperf session capture synchronized with the runtime diagnostic window.
- Perfetto starts before target launch and stops before YPower's cleanup force-stop; OEM-incompatible configs retry with a minimal scheduler/process configuration.
- simpleperf uses --app launch waiting, exports perf.data plus a call-graph text report, and enriches matching native findings by DSO.
- Added Java System.load/System.loadLibrary stacks, ByteHook dlopen callbacks and filtered dlsym tracing for JNI/linker mapping.
- Added Java→SO→symbol mapping output and excluded instrumentation-only events from causal attribution.

- Added centralized DetectionRuleCatalog and a four-state detection model: CHECKED/HIT/NOT_HIT/UNKNOWN.
- Fixed Java and native path matching so specific Magisk/KernelSU/APatch/su rules win before generic /data/adb.
- Package enumeration now emits one event per sensitive returned package instead of keeping only the first.
- /proc maps/status/mount and command-execution observations no longer count as positive hits without a concrete result.
- Direct package NameNotFound results are NOT_HIT and no longer gain attribution weight.
- Correlation now caps CHECKED evidence and excludes NOT_HIT/UNKNOWN from causal attribution.
- Recommendation generation now maps directly from ruleId to the rule catalog instead of keyword matching.

- Added precise attribution events with stable Rule IDs, input, actual result, match state, exception, duration, PID/TID/thread, session ID and stack.
- Split permission observation from permission simulation so diagnostic mode can record the real permission result without changing it.
- Added Java debugger checks and deeper exit call stacks.
- Replaced time-only attribution with combined timing, result-hit, same PID/TID, shared-stack, native-module and repeated-hit scoring.
- Added optional Deep-mode ByteHook 1.1.2 native observer for access/fopen/stat/lstat/readlink/ptrace and native exit functions.
- Added native caller SO+offset and compact native backtraces; native diagnostics remain observe-only.

- Expanded attribution output with GitHub project rationale: why the app checks a signal, what the referenced project says, why YPower attributed it, and how to repair or investigate.
- Renamed the Advice view to Attribution explanation.
- Root/Hook/integrity guidance remains compatibility-oriented and does not provide concealment/bypass steps.

- Switched fix advice to attribution-driven recommendations.
- Only primary and qualified secondary causal findings can generate recommendations.
- Ordinary observed checks no longer generate generic advice.
- Added explicit primary/secondary attribution labels and scores to simple/detailed/advice views.
- Suppress recommendations when there is no real exit or causal evidence is insufficient.

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
