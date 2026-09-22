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
- 自动归因：按本次运行事件与真实退出时间的接近程度计算关联度，而不是看到设备存在 Root 就直接判定 Root 导致闪退。

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
