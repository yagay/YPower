# 应用增强 · YPower

YPower 是一个面向 Root / LSPosed 设备的 Android 应用增强与诊断工具。它的核心原则是：**目标应用继续安装在 `/data/app`，不把第三方 APK 塞进 `/system/app` 或 `/system/priv-app`**。

## 已实现

### 应用增强

- 在 YPower 应用列表中直接启用/停用目标应用。
- Root 增强：
  - Doze 白名单。
  - `RUN_IN_BACKGROUND` / `RUN_ANY_IN_BACKGROUND` / `START_FOREGROUND` AppOps。
  - App Standby Active。
  - 后台数据白名单。
  - 自动授予目标 APK 已声明、且 Android 允许通过 `pm grant` 授予的 dangerous 权限。
  - 开机后自动恢复增强策略。
- LSPosed API 102：
  - 使用动态 scope，请求作用域由 YPower 自己完成。
  - Remote Preferences 保存每个包的配置。
  - 模拟目标应用进程看到的 `System App` 身份。
  - 模拟目标应用自身权限检查为 `GRANTED`（**仅改变应用侧检查，不等于获得 signature/privileged 权限**）。
  - Java 运行时追踪：文件、包查询、权限查询、调试器状态、系统属性、`Runtime.exec`、`ProcessBuilder`、主动退出等。
  - 深度诊断可选启用 ByteHook 1.1.2，只观察目标进程 native 侧的敏感路径、`ptrace` 与 `abort/exit/kill`，不修改原函数返回值。

### 推荐应用

- 主界面提供“推荐应用”入口，只显示当前设备已安装且命中内置规则的应用。
- 推荐项会显示“推荐原因”和“建议 Hook 组合”。
- 一键应用会同时：
  - 启用 YPower；
  - 写入推荐的 Root/Hook 配置；
  - 同步 Remote Preferences；
  - 请求加入 LSPosed 动态 Scope；
  - 立即应用 Root 侧后台增强。
- 首批内置规则：抖音、抖音极速版、红果免费短剧、红果短剧海外版。
- 推荐配置默认以诊断型 Hook 为主，不自动开启“系统身份模拟/权限状态模拟”这类会改变目标 App 行为的功能。

### 应用诊断

诊断中心现在是**运行时会话模式**，不再把“手机当前存在 Root/LSPosed/Bootloader 状态”等静态环境扫描直接当作目标 App 的诊断结果。

流程：

1. 开始诊断：YPower 临时打开本次需要的目标进程追踪 Hook，并在测量窗口开始前停止旧目标进程。
2. 启动目标 App：正常操作并复现问题。
3. 返回 YPower，点击“结束并分析”。
4. YPower 先关闭测量窗口，再停止目标进程卸载临时 Hook，并恢复该 App 原来的 YPower 配置。

报告只显示本次运行**实际触发过**的事件，例如：

- 包/安装环境查询。
- `/proc/self/maps`、TracerPid、mount、`/data/adb`、Root 路径等实际文件访问。
- Bootloader/AVB、debuggable、secure 等实际系统属性查询。
- `Runtime.exec` / `ProcessBuilder` 实际敏感命令。
- 权限状态查询。
- `System.exit` / `Runtime.halt` / `killProcess` 主动退出。
- 本次会话内真实的 Java/Native Crash、ANR、低内存或 signal 退出。

**没有实际发生的检测项不会显示“通过/未通过”，也不会出现在结果列表里。**

三个采集级别和三个展示级别仍然互相独立：

- 快速 / 标准 / 深度诊断。
- 简要 / 详细 / 原始结果。
- JSON 报告导出。
- 自动归因：使用稳定 Rule ID，并综合 **时间距离、实际返回结果是否命中、PID/TID、共同调用栈、同一 native SO、同一会话重复命中** 计算关联度，而不是看到设备存在 Root 就直接判定 Root 导致闪退。

## 设计边界

YPower 不通过全局修改 PermissionManager 或默认 Hook `system_server` 来“让所有权限永远通过”。这样做会显著增加系统重启/bootloop 风险。

权限处理优先级：

1. Android 本来允许授予的权限 → 真正授予。
2. Doze/AppOps/后台策略 → Root 从目标进程外部调整。
3. 只是目标 App 自己的身份/权限判断 → 目标进程 LSPosed 兼容。
4. signature / privileged / platform-signed 能力不能靠一个 `GRANTED` 返回值变成真正权限；这类能力后续应通过受控的 privileged proxy 实现，而不是全局放宽系统权限检查。

因此，目标 App 的 Hook 出错通常只影响该目标进程，而不是 Android Framework。

## 环境

- Android 12+ (`minSdk 31`)
- compile/target SDK 37
- Java 17 bytecode
- Gradle 9.4.1
- Android Gradle Plugin 9.2.1
- libsu 6.0.0
- libxposed API / Service 102.0.0
- Root（推荐 KernelSU / Magisk 等提供标准 `su` 的环境）
- LSPosed/兼容 API 102 框架：仅高级兼容/Java 行为追踪需要；Root 增强和快速诊断不依赖目标 App Hook。

## 构建

```bash
gradle :app:assembleDebug
```

仓库包含 GitHub Actions，推送到 `main` 后会自动构建 debug APK。

## 首次使用

1. 安装 YPower。
2. 授予 YPower Root。
3. 如果需要身份模拟/权限状态模拟/Java 环境追踪，在 LSPosed 中**只需要启用 YPower 模块本身一次**。
4. 后续目标应用在 YPower 列表中添加；YPower 会请求动态 scope。
5. 首次给某个 App 开启 LSPosed 功能后，重新启动目标 App 一次以加载 Hook。

## 诊断状态含义

- `检测到`：本次运行中目标 App 实际执行了该项检查。
- `异常`：本次会话记录到了退出、崩溃、ANR 或明确异常终点。
- 没有发生的检测项不会显示。
- “检测到”不等于“导致退出”；只有归因引擎达到阈值时才标为主要/次要归因。

## 开源参考

YPower 为独立实现，没有复制这些项目的 GPL 源码。架构和检测项目参考了公开项目/文档的思路，包括：libsu、Shizuku/Sui、App Manager、libxposed/LSPosed、RootBeer、RootRoot、Ruru、DuckDetector、DirtySepolicy、xCrash、Matrix、ByteHook、ShadowHook。

详见 [ARCHITECTURE.md](ARCHITECTURE.md) 与 [THIRD_PARTY.md](THIRD_PARTY.md)。

## License

Apache-2.0


### 归因驱动建议

诊断中心的“建议”不是按检测项逐条生成，而是先完成运行时归因，再决定是否给建议：

- 只有“主要归因”和满足阈值的“次要归因”会生成建议。
- 普通检测项即使被观察到，只要与退出关联不足，就不会生成建议。
- 主要归因最低要求：非退出检测项关联分数 >= 40。
- 次要归因要求：关联分数 >= 55，且与主要归因分差不超过 20。
- 没有真实退出/崩溃，或归因证据不足时，不生成修复建议。
- 建议会显示其对应的归因项、关联分数以及参考的开源项目/诊断思路。


### 归因说明内容

“归因说明”只针对主要归因和符合阈值的次要归因生成，并固定展示：

- 为什么检测：说明目标 App 为什么会读取这类环境/权限/崩溃信息。
- 开源项目说明：结合 RootBeer、xCrash、Matrix、App Manager、ByteHook、ShadowHook 等项目 README/手册解释这类信号代表什么、有什么局限。
- 为什么这次归因：说明本次运行中 YPower 为什么把它列为主要/次要归因，包括关联分数和同一会话证据。
- 应该怎样修复/排查：给出兼容性和故障排查方向，不自动执行修改，也不提供隐藏或绕过安全检测的方案。
- 参考：标明对应的开源项目或 Android 机制。

例如 Root 检测不会写成“检测到 Root，所以 Root 导致闪退”，而是说明 RootBeer 将 su、Root 管理应用、test-keys、危险属性等视作 Root 的“迹象”，且明确存在误报和局限；只有当同一运行会话中 Root 检测与真实退出稳定紧邻时，YPower 才会把它提升为归因候选。


### 精准归因数据模型

每个运行时检测事件会记录：

- `ruleId`：稳定规则 ID，例如 `ROOT_FILE_SU`、`HOOK_PROC_MAPS`、`PROP_DEBUGGABLE`、`NATIVE_PTRACE`。
- 输入参数：实际路径、包名、property key、命令或权限名。
- 返回结果：例如 `File.exists=true`、`access=0`、property 实际值、权限查询结果。
- 是否明确命中：区分“应用做了检查”和“检查结果真的命中了可疑状态”。
- 异常、耗时、PID、TID、线程名、进程名、session ID。
- Java 调用栈；主动退出点最多记录 32 帧。
- 深度诊断中的 native 调用者 SO + offset 和精简 native backtrace。

归因分数由以下证据共同组成：

- 离真实退出的时间距离。
- 检测返回结果是否明确命中。
- 与退出是否同 PID/TID。
- 检测栈和退出栈是否存在共同业务帧。
- Native 检测与退出是否来自同一 SO。
- 同一诊断会话中是否重复命中。
- 调用是否抛出真实异常。

只有存在真实退出/崩溃，而且分数达到阈值时，才会产生主要/次要归因。

### Native 深度诊断

深度模式才会启用 ByteHook。当前观察：

- `access`
- `fopen`
- `stat`
- `lstat`
- `readlink`
- `ptrace`
- `abort`
- `exit` / `_exit`
- `kill` / `tgkill`

Native tracer 使用 caller filter 排除系统/APEX/vendor、ByteHook 自身和 YPower 自身库，减少噪声与递归风险。它只记录原始调用和返回结果，不隐藏 Root/Hook/调试器环境，也不修改检测结果。


### RuleCatalog 与四态检测

YPower 不再把所有运行时事件简化成 matched=true/false。每条规则统一由 DetectionRuleCatalog 定义，并使用四种状态：

- HIT：检查真实返回了该规则定义的风险/异常结果。
- CHECKED：目标 App 确实执行了检查，但当前观测还不能证明检查结果命中。
- NOT_HIT：检查发生了，并且结果明确没有命中。
- UNKNOWN：信息不足，无法判断结果。

关键约束：

- /proc/self/maps、/proc/self/status、mountinfo、/data/adb 仅因“成功访问”不会变成 HIT。
- Runtime.exec/ProcessBuilder 只观察到命令被执行时记为 CHECKED；没有 stdout/exit code 前不宣称命中。
- PackageManager 直接查询目标包并成功返回才算 HIT；NameNotFoundException 算 NOT_HIT。
- getInstalledPackages/getInstalledApplications 会把返回列表中的每个敏感包拆成独立 PACKAGE_* 事件，不再只保留第一个。
- Root 文件规则按最具体路径优先匹配，Magisk/KernelSU/APatch/su 不会被通用 /data/adb 规则吞掉。
- FixRecommendationEngine 直接按 ruleId 查询 RuleCatalog，不再通过 root/su/magisk 等关键词猜建议。
- NOT_HIT 与 UNKNOWN 不进入主要归因；CHECKED 只有在同线程或共享业务调用栈等结构化证据足够强时，才可能作为低可信候选。


### 深度系统监控

深度诊断现在额外启用三类监控：

- Perfetto：从目标 App 启动前开始记录调度、进程生命周期、am/wm/pm/dalvik/binder_driver 等系统时间线；结束诊断时先停止采集，再由 YPower 自己 force-stop，避免把 YPower 的停止动作混入测量窗口。部分 OEM 不支持完整 atrace 配置时会自动退化为 scheduler + process stats 最小配置。
- simpleperf：使用 --app 等待目标进程启动并记录 native call graph；结束后生成 perf.data 和文本报告。YPower 会把命中同一个 SO 的采样片段附到对应 native finding。
- JNI / Linker mapping：Java 侧记录 System.load/System.loadLibrary 调用栈，Native 侧记录 ByteHook dlopen callback 和筛选后的 dlsym（JNI_OnLoad、Java_*、RegisterNatives 及 root/debug/security/integrity/check 等相关符号），并按时间与 TID 建立 Java→SO→symbol 映射。

这些数据属于诊断证据，不修改目标 App 返回值，也不会把单纯的库加载事件当成安全风险归因。


### 异常传播层

运行时诊断现在增加“检测 → 异常传播 → Fatal/退出”中间层，并保持只观察、不接管异常：

- Java Fatal：Hook `Thread.dispatchUncaughtException(Throwable)`，在目标 App 原有 `UncaughtExceptionHandler` 之前记录 Throwable，然后原样继续处理链。
- Handler 安装：记录 `Thread.setDefaultUncaughtExceptionHandler` 与线程级 `setUncaughtExceptionHandler`，用于识别 Crashlytics/Bugly/Sentry/自有 Handler 等最终处理者。
- Kotlin Coroutine：如果目标 APK 包含 kotlinx.coroutines，动态 Hook `CoroutineExceptionHandlerKt.handleCoroutineException(...)`。
- RxJava2/3：如果对应版本存在，动态 Hook `RxJavaPlugins.onError(Throwable)` 与 `setErrorHandler(...)`。
- 可选框架通过目标 App 的默认 ClassLoader 查找；没有依赖时不会安装对应 Hook。

每条异常事件记录 exceptionClass、message、cause、suppressed 数量、Throwable identity、PID/TID/线程、完整 Throwable 业务栈和 sessionId。

YPower 会把相同 Throwable identity 的 Coroutine/RxJava 事件与 Java uncaught 直接串联；如果 identity 不同但同 TID 且时间紧邻，也会记录弱传播关系。

安全规则的归因不会被异常事件取代：error 类 finding 不参与主要/次要安全原因竞争，而是作为中间证据。安全检测与 Java Fatal 同线程、时间紧邻或共享业务调用栈时，会获得额外的“检测 → 异常 → 退出”因果分。


### DuckDetector 补盲监控

参考 DuckDetector 当前公开源码后，YPower 新增了一批“目标 App 实际检查行为”观察点，而不是把 Duck 的主动设备扫描照搬进来。

新增 Native/路径监控：

- Native property：`__system_property_get`，继续映射到 `PROP_VERIFIED_BOOT`、`PROP_VBMETA_STATE`、`PROP_DEBUGGABLE` 等现有 Rule ID。
- Kernel：`uname`、`/proc/version`、`/proc/cmdline`、`/proc/sys/kernel/osrelease`、`/proc/sys/kernel/version`、`kptr_restrict`。
- SELinux：`/sys/fs/selinux/*`、`/proc/self/attr/current`、`getxattr/lgetxattr` 的 SELinux 属性查询。
- Memory/Zygisk 风格检查：`/proc/self/smaps`、`/proc/self/fd`、`/proc/self/task`、`sigaction`、`getauxval(AT_SYSINFO_EHDR)`、`mprotect(PROT_EXEC)`、`dl_iterate_phdr`。
- libc 文件入口补齐：`open`、`openat`、`opendir`，与已有 access/fopen/stat/lstat/readlink 一起工作。

这些规则默认属于 CHECKED，除非有明确返回值能证明具体风险状态；读取一个 proc/SELinux 文件本身不会直接变成 HIT。

### Raw syscall 实验模式

目标 App 设置中新增：

`Raw syscall 实验追踪（strace/ptrace；可能触发反调试，默认关闭）`

只有在：

- 诊断级别为“深度”；
- 用户显式打开该 App 的 raw syscall 开关；
- 设备有 root 且存在 strace；

时才启动。当前观察 openat/readlinkat/ptrace/prctl/ioctl/mmap/mprotect/execve/kill/tgkill，并保存 `syscall-trace.txt`。

因为 strace 本身使用 ptrace，可能改变 TracerPid 或触发目标 App 的反调试逻辑，所以它只作为实验验证层，不参与默认诊断。
