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
  - 敏感环境行为追踪：文件、包查询、系统属性、`Runtime.exec`、`ProcessBuilder`、`android.system.Os`、主动退出等。

### 应用诊断

三个采集级别和三个展示级别互相独立：

- 快速 / 标准 / 深度诊断。
- 简要 / 详细 / 原始结果。
- JSON 报告导出。
- 自动归因：把环境检查、YPowerTrace 事件和异常退出时间关联起来，输出“高度相关 / 存在关联 / 数据不足”，避免把环境存在直接等同于崩溃原因。

当前规则包含：

- Root、Magisk、KernelSU、APatch、Root modules。
- LSPosed/Xposed、Zygisk、Riru、Frida、注入 maps、可疑线程。
- `/proc`、mount、mount namespace、RWX 内存、ELF/SO、FD、线程、meminfo。
- SELinux、模块 sepolicy、`avc: denied`。
- Bootloader / Verified Boot / vbmeta / build tags / debuggable / secure。
- 模拟器、虚拟空间/双开、开发者选项、ADB。
- Proxy、VPN/tunnel、Mock Location、Accessibility。
- APK 签名、APK SHA-256、安装来源。
- Runtime permission、AppOps、悬浮窗 AppOp。
- Java crash、Native crash、ANR、SecurityException、JNI/linker/ABI、OOM/LMKD、WebView/Chromium、SQLite、TLS。
- ActivityManager exit-info、logcat、进程树、`/proc/status`。
- Play Integrity、Key Attestation、服务端风控明确标注为 UNKNOWN，除非目标应用自身暴露可关联证据。

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

- `通过`：当前采集未发现该类异常信号。
- `未通过`：明确发现该类环境/异常信号；不代表它一定是闪退原因。
- `警告`：存在值得关注的信息，但可能是合法/正常情况。
- `未知`：没有足够数据、目标进程未运行，或判断发生在 Google/服务端一侧。

## 开源参考

YPower 为独立实现，没有复制这些项目的 GPL 源码。架构和检测项目参考了公开项目/文档的思路，包括：libsu、Shizuku/Sui、App Manager、libxposed/LSPosed、RootBeer、RootRoot、Ruru、DuckDetector、DirtySepolicy、xCrash、Matrix、ByteHook、ShadowHook。

详见 [ARCHITECTURE.md](ARCHITECTURE.md) 与 [THIRD_PARTY.md](THIRD_PARTY.md)。

## License

Apache-2.0
