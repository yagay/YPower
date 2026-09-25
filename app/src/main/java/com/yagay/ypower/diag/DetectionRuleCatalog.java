package com.yagay.ypower.diag;

import com.yagay.ypower.hook.DetectionRuleIds;
import com.yagay.ypower.model.DetectionHitState;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DetectionRuleCatalog {
    private static final Map<String, DetectionRuleDefinition> RULES = new LinkedHashMap<>();

    static {
        add(DetectionRuleIds.ROOT_FILE_SU, "root", "su 二进制文件检测",
                "应用检查传统 su 路径，是为了判断设备上是否存在可直接获得高权限的 su 二进制文件。",
                "RootBeer 将 su binary 作为 Root 的一个信号，同时明确提醒单一信号并不能 100% 证明整个设备状态。",
                "在应用官方支持的未修改设备状态下做 A/B 复测；如果是自己的应用，不要让单一 su 信号直接触发强制退出。",
                "RootBeer / RootRoot");

        add(DetectionRuleIds.ROOT_FILE_MAGISK, "root", "Magisk 路径检测",
                "应用检查 Magisk 相关路径，是为了识别常见 systemless Root 环境。",
                "RootBeer、RootRoot 等项目把 Root 管理器、文件和 systemless 痕迹作为多种环境信号的一部分，而不是单一绝对结论。",
                "确认该检查是否真实命中且稳定紧邻退出；第三方应用应在其官方支持环境下复测，自有应用应避免单一信号直接退出。",
                "RootBeer / RootRoot");

        add(DetectionRuleIds.ROOT_FILE_KERNELSU, "root", "KernelSU 路径检测",
                "应用检查 KernelSU 相关路径，是为了识别内核级 Root/管理环境的痕迹。",
                "Root/环境检测项目通常会把已知管理器、文件、mount 与属性组合成多因素信号。",
                "确认具体路径和返回结果；仅当相同 HIT 多次紧邻退出时才作为主要环境归因。",
                "RootRoot / Ruru");

        add(DetectionRuleIds.ROOT_FILE_APATCH, "root", "APatch 路径检测",
                "应用检查 APatch 相关路径，是为了识别另一类 Root/系统修改环境。",
                "环境检测项目通常依赖多种文件、包和系统属性信号，单项命中不应被当成绝对结论。",
                "确认具体路径和返回结果，并在官方支持设备状态下 A/B；自有应用应将单项信号降级为风险证据而非直接退出条件。",
                "RootRoot / Ruru");

        add(DetectionRuleIds.ROOT_DATA_ADB, "mount", "/data/adb 环境访问",
                "应用访问 /data/adb，通常是为了观察 Root 模块、systemless 组件或相关文件结构。",
                "现代 Root 不一定修改 /system 本体，因此 mount 和 /data/adb 会成为额外环境信号。",
                "把它视为 CHECKED，只有读到更具体的 Root 目标或与退出形成稳定调用链时再提升归因。",
                "Root detection / systemless environment model");

        add(DetectionRuleIds.HOOK_PROC_MAPS, "hook", "/proc/self/maps 注入环境检查",
                "应用读取自身 maps，通常是为了查看已加载 SO、映射区或运行时注入痕迹。",
                "LSPosed/Frida/Hook 类检测经常会检查 maps；但“成功读取 maps”只表示执行了检查，不代表已经发现 Hook。",
                "只有后续规则实际识别出具体映射目标，或该检查与退出共享调用链并稳定复现时，才提高可信度。",
                "LSPosed / ByteHook / ShadowHook diagnostics");

        add(DetectionRuleIds.DEBUG_PROC_STATUS, "debugger", "/proc/self/status 调试状态检查",
                "应用读取 status 通常是为了查看 TracerPid 等调试状态。",
                "打开 /proc/self/status 本身不是调试器命中；只有实际解析到非零 TracerPid 等结果才能视为 HIT。",
                "保持为 CHECKED，结合 Debug API、ptrace 或实际 TracerPid 结果再归因。",
                "Android debugger diagnostics");

        add(DetectionRuleIds.MOUNT_PROC_MOUNT, "mount", "Mount namespace 检查",
                "应用读取 mountinfo 或 mounts，是为了观察 overlay、bind mount、systemless Root 或异常挂载。",
                "RootBeer 等项目指出传统“system 是否可写”不足以覆盖 systemless Root，因此 mount 线索经常作为补充。",
                "读取 mount 文件本身只算 CHECKED；只有解析到具体可疑挂载后才应标记 HIT。",
                "RootBeer / proc-mount diagnostics");

        addPackage(DetectionRuleIds.PACKAGE_MAGISK, "Magisk 包名检测", "Magisk", "RootBeer / Ruru");
        addPackage(DetectionRuleIds.PACKAGE_KERNELSU, "KernelSU 包名检测", "KernelSU", "Ruru / RootRoot");
        addPackage(DetectionRuleIds.PACKAGE_APATCH, "APatch 包名检测", "APatch", "Ruru / RootRoot");
        addPackage(DetectionRuleIds.PACKAGE_LSPOSED, "LSPosed 包名检测", "LSPosed", "LSPosed / Ruru");
        addPackage(DetectionRuleIds.PACKAGE_XPOSED, "Xposed 包名检测", "Xposed", "Xposed / Ruru");
        addPackage(DetectionRuleIds.PACKAGE_FRIDA, "Frida 包/组件检测", "Frida", "Frida / RASP detection projects");
        addPackage(DetectionRuleIds.PACKAGE_SHIZUKU, "Shizuku 包名检测", "Shizuku", "Shizuku / package-query diagnostics");

        add(DetectionRuleIds.PACKAGE_ENUMERATION, "package", "已安装应用枚举",
                "应用枚举已安装包可能用于功能发现、兼容性判断或环境风险检查。",
                "枚举动作本身不能说明命中了敏感包；应把返回列表中的每个具体敏感目标拆成独立 Rule 事件。",
                "保持为 CHECKED；只有具体 PACKAGE_* 规则返回目标包时才算 HIT。",
                "Android PackageManager / RootBeer");

        addProperty(DetectionRuleIds.PROP_VERIFIED_BOOT, "Verified Boot 状态检测",
                "读取 verified boot 状态用于了解启动链完整性。", "Android Verified Boot");
        addProperty(DetectionRuleIds.PROP_VBMETA_STATE, "VBMeta 状态检测",
                "读取 vbmeta device state 用于了解设备启动验证状态。", "Android Verified Boot");
        addProperty(DetectionRuleIds.PROP_FLASH_LOCKED, "Bootloader Lock 状态检测",
                "读取 flash lock 状态用于了解 bootloader 是否锁定。", "Android Verified Boot");
        addProperty(DetectionRuleIds.PROP_DEBUGGABLE, "ro.debuggable 检测",
                "应用读取 ro.debuggable 用于判断系统是否是可调试构建。", "RootBeer / Android build properties");
        addProperty(DetectionRuleIds.PROP_SECURE, "ro.secure 检测",
                "应用读取 ro.secure 用于判断系统安全属性。", "RootBeer / Android build properties");
        addProperty(DetectionRuleIds.PROP_BUILD_TAGS, "Build Tags 检测",
                "应用读取 build tags 常用于观察 test-keys 等非标准构建信号。", "RootBeer");
        addProperty(DetectionRuleIds.PROP_BUILD_TYPE, "Build Type 检测",
                "应用读取 build type 用于了解 user/userdebug/eng 等构建类型。", "Android build properties");
        add(DetectionRuleIds.PROP_GENERIC, "environment", "系统属性检查",
                "应用读取环境相关 property 以判断设备构建或运行环境。",
                "只有具体 property 的实际返回值能决定它是否真正命中某种风险状态。",
                "查看具体 key 和返回值；UNKNOWN/CHECKED 不作为强归因依据。",
                "Android SystemProperties");

        addCommand(DetectionRuleIds.CMD_SU, "su 命令检测",
                "应用执行 which su 或 su 相关命令通常是为了查询 su 是否可执行。");
        addCommand(DetectionRuleIds.CMD_GETPROP, "getprop 环境查询",
                "应用通过 shell 读取系统属性以检查运行环境。");
        addCommand(DetectionRuleIds.CMD_MOUNT, "mount 命令检查",
                "应用执行 mount 相关命令通常是为了查看挂载环境。");
        addCommand(DetectionRuleIds.CMD_SELINUX, "SELinux 状态检查",
                "应用执行 getenforce 用于读取 SELinux 状态。");

        add(DetectionRuleIds.DEBUG_IS_CONNECTED, "debugger", "Debugger 连接检测",
                "应用调用 Debug.isDebuggerConnected() 判断当前进程是否连接 Java 调试器。",
                "返回 true 才是 HIT；false 是明确 NOT_HIT。",
                "如果 HIT 与退出稳定相关，关闭开发调试环境后复测；自有应用应避免把单一调试信号直接变成不透明退出。",
                "Android Debug API");
        add(DetectionRuleIds.DEBUG_WAITING, "debugger", "Debugger 等待状态检测",
                "应用调用 Debug.waitingForDebugger() 判断是否正在等待调试器。",
                "返回 true 才是 HIT；false 是明确 NOT_HIT。",
                "如果 HIT 与退出稳定相关，关闭调试会话后复测。",
                "Android Debug API");

        add(DetectionRuleIds.NATIVE_PTRACE, "debugger", "Native ptrace 检查",
                "Native 代码调用 ptrace 可能用于调试控制、反调试或进程跟踪。",
                "单次 ptrace 调用或 EPERM 并不能独立证明存在调试器，因此默认只视为 CHECKED。",
                "结合 /proc/self/status、Debug API 和退出调用链综合判断。",
                "Android/Linux ptrace diagnostics");

        add(DetectionRuleIds.PERMISSION_QUERY, "permission", "权限状态查询",
                "应用检查权限是为了确认受保护 API 是否可用。",
                "真实系统权限/AppOps 与应用侧查询是不同层级；返回 DENIED 只表示权限状态问题，不是安全环境命中。",
                "根据具体 permission、SecurityException 和系统 AppOps 状态排查，不要用查询结果模拟代替真实授权。",
                "App Manager / Android permissions");

        addExit(DetectionRuleIds.EXIT_SYSTEM, "System.exit 主动退出");
        addExit(DetectionRuleIds.EXIT_HALT, "Runtime.halt 主动退出");
        addExit(DetectionRuleIds.EXIT_KILL_PROCESS, "killProcess 主动退出");
        addExit(DetectionRuleIds.EXIT_NATIVE_ABORT, "Native abort 主动退出");
        addExit(DetectionRuleIds.EXIT_NATIVE_EXIT, "Native exit/_exit 主动退出");
        addExit(DetectionRuleIds.EXIT_NATIVE_KILL, "Native kill/tgkill 主动退出");

        add(DetectionRuleIds.KERNEL_UNAME_QUERY, "kernel", "Kernel uname 查询",
                "应用通过 uname/syscall 查询内核版本与构建身份。",
                "DuckDetector 会把 uname、/proc/version、os.version 等多个来源做一致性比较。",
                "记录实际返回值与调用栈；如果只是查询则保持 CHECKED，只有后续命中具体异常特征才升级归因。",
                "DuckDetector Kernel Check / Linux uname");
        add(DetectionRuleIds.KERNEL_PROC_VERSION, "kernel", "/proc/version 查询",
                "应用读取 /proc/version 以获取内核编译与版本信息。",
                "DuckDetector 会把 /proc/version 与 uname、sysctl 等来源交叉比较。",
                "仅表示执行了检查；结合具体内容和退出链判断。",
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_CMDLINE_QUERY, "kernel", "/proc/cmdline 查询",
                "应用读取 boot cmdline 以检查启动参数、内核参数或修改痕迹。",
                "DuckDetector 对 boot cmdline 有独立规则扫描。",
                "仅表示检查发生；不要因为读取成功就直接判定异常。",
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_OSRELEASE_QUERY, "kernel", "kernel osrelease 查询",
                "应用读取 /proc/sys/kernel/osrelease 获取运行内核版本。",
                "DuckDetector 将其作为内核身份一致性来源之一。",
                "结合 uname/System.getProperty(os.version) 做一致性分析。",
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_SYS_VERSION_QUERY, "kernel", "kernel version 查询",
                "应用读取 /proc/sys/kernel/version 获取内核构建信息。",
                "DuckDetector 将其作为内核身份一致性来源之一。",
                "结合其他内核身份来源交叉确认。",
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_KPTR_QUERY, "kernel", "kptr_restrict 查询",
                "应用读取 kptr_restrict 判断内核指针暴露策略。",
                "DuckDetector 将 kptr_restrict 作为信息性内核安全状态。",
                "仅记录实际值与上下文，不把它单独当成 Root 原因。",
                "DuckDetector Kernel Check");

        add(DetectionRuleIds.SELINUX_ENFORCE_READ, "selinux", "SELinux enforcing 状态读取",
                "应用读取 selinuxfs enforcing 状态。",
                "DuckDetector 同时比较 selinuxfs、getenforce、proc attr 等来源。",
                "仅表示 SELinux 状态检查发生；结合实际值和多来源一致性判断。",
                "DuckDetector SELinux");
        add(DetectionRuleIds.SELINUX_CONTEXT_READ, "selinux", "SELinux 进程上下文读取",
                "应用读取 /proc/self/attr/current 获取当前进程 SELinux context。",
                "DuckDetector 会进一步分析 context 类型与 policy 一致性。",
                "记录真实 context 与调用链；不要把读取动作本身当异常。",
                "DuckDetector SELinux");
        add(DetectionRuleIds.SELINUX_POLICY_READ, "selinux", "SELinux policy/selinuxfs 查询",
                "应用访问 /sys/fs/selinux 下的 policy/status/class 等信息。",
                "DuckDetector 会检查 policy version、security classes、permissive domain 与 policyload 状态。",
                "仅记录具体路径和返回结果；异常归因需结合后续退出。",
                "DuckDetector SELinux");
        add(DetectionRuleIds.SELINUX_XATTR_QUERY, "selinux", "SELinux xattr 查询",
                "应用通过 getxattr/lgetxattr 查询 security.selinux 等扩展属性。",
                "DuckDetector 用文件/context 一致性作为 SELinux 完整性证据之一。",
                "记录目标路径、attribute 名和返回结果，不修改原值。",
                "DuckDetector SELinux / Linux xattr");

        add(DetectionRuleIds.MEMORY_SMAPS_QUERY, "memory", "/proc/self/smaps 查询",
                "应用读取 smaps 获取更细粒度的映射、权限与内存区域信息。",
                "DuckDetector 的 Memory/Zygisk 模块会分析 smaps、匿名映射和可疑 loader 痕迹。",
                "仅表示内存完整性检查发生；具体异常需由内容分析或后续规则确认。",
                "DuckDetector Memory / Zygisk");
        add(DetectionRuleIds.MEMORY_FD_QUERY, "memory", "进程 FD 扫描",
                "应用扫描 /proc/self/fd 或其他进程 fd 以寻找 memfd、deleted SO、设备句柄等。",
                "DuckDetector Memory/Zygisk 都包含 FD probe。",
                "记录具体 fd 路径与 readlink 结果；单纯扫描保持 CHECKED。",
                "DuckDetector Memory / Zygisk");
        add(DetectionRuleIds.MEMORY_TASK_QUERY, "memory", "线程/Task 扫描",
                "应用扫描 /proc/self/task 等线程信息。",
                "DuckDetector Zygisk 模块包含 thread probe。",
                "记录实际访问与调用栈；只有具体命中线程特征后再升级。",
                "DuckDetector Zygisk");
        add(DetectionRuleIds.MEMORY_LINKER_ENUM_QUERY, "memory", "Linker 模块枚举",
                "应用调用 dl_iterate_phdr 等方式枚举已加载 ELF 模块。",
                "DuckDetector 会比较 linker 视图与 /proc/self/maps 是否一致。",
                "作为 CHECKED 证据；若后续发现 maps/linker 不一致再形成具体 finding。",
                "DuckDetector Memory linker detector");
        add(DetectionRuleIds.MEMORY_SIGNAL_QUERY, "memory", "Signal handler 查询/设置",
                "应用查询或设置 sigaction，可能用于检查 SIGTRAP/SIGSEGV 等 handler 是否异常。",
                "DuckDetector Memory 模块检查多个 signal handler 的落点与映射来源。",
                "记录 signal、handler 与调用栈，不修改 handler。",
                "DuckDetector Memory signal detector");
        add(DetectionRuleIds.MEMORY_VDSO_QUERY, "memory", "vDSO / auxv 查询",
                "应用通过 getauxval 等方式检查 vDSO 基址和运行时布局。",
                "DuckDetector 会比较 AT_SYSINFO_EHDR 与 [vdso] mapping。",
                "记录查询结果并与 maps 信息关联；单次查询保持 CHECKED。",
                "DuckDetector Memory vDSO detector");
        add(DetectionRuleIds.MEMORY_MPROTECT_QUERY, "memory", "内存权限修改/检查",
                "应用调用 mprotect 改变或验证内存页权限，常见于完整性、自保护和 JIT 场景。",
                "这不是单独的风险命中，但与可执行匿名映射或 Hook 检查结合时有诊断价值。",
                "记录地址、长度、prot 与调用者 SO，仅用于归因。",
                "Linux mprotect / DuckDetector memory model");

        add(DetectionRuleIds.KEYSTORE_INSTANCE_QUERY, "attestation", "AndroidKeyStore / KeyStore 实例查询",
                "应用初始化 KeyStore/AndroidKeyStore，可能用于普通密钥操作，也可能是后续硬件背书链的入口。",
                "KeyAttestation/SPIC 等项目都会经过 AndroidKeyStore/KeyMint 相关 API；单纯 getInstance 只表示 CHECKED。",
                "结合后续 attestation challenge、StrongBox、certificate chain 和退出链判断，不把普通 KeyStore 使用误判成安全检测。",
                "AndroidKeyStore / KeyAttestation");
        add(DetectionRuleIds.KEY_ATTESTATION_CHALLENGE, "attestation", "Key Attestation Challenge",
                "应用设置 attestation challenge，明确表示它准备请求设备/密钥硬件背书。",
                "KeyAttestation 项目使用 challenge 生成可验证证书链；这比普通 KeyStore 调用更接近完整性校验。",
                "记录 challenge 长度、调用栈和后续证书链查询；不修改 challenge 或 attestation 结果。",
                "Android Key Attestation / KeyMint");
        add(DetectionRuleIds.KEY_STRONGBOX_REQUEST, "attestation", "StrongBox 请求",
                "应用显式要求 StrongBox-backed key，通常用于更强硬件隔离保证。",
                "StrongBox/KeyMint 是 Android 硬件密钥安全层的一部分。",
                "记录是否请求 StrongBox 以及后续是否失败/降级，不修改原配置。",
                "Android StrongBox / KeyMint");
        add(DetectionRuleIds.KEY_CERT_CHAIN_QUERY, "attestation", "Attestation 证书链查询",
                "应用读取 AndroidKeyStore certificate chain，常用于解析设备/密钥 attestation extension。",
                "KeyAttestation 会解析证书链和 attestation extension 来判断安全级别与设备状态。",
                "记录 alias、链长度和调用栈；若后续出现异常/退出再作为归因证据。",
                "KeyAttestation / AndroidKeyStore");
        add(DetectionRuleIds.KEY_SECURITY_LEVEL_QUERY, "attestation", "Key 安全级别查询",
                "应用读取 KeyInfo/安全级别信息，用于区分软件、TEE 或 StrongBox 实现。",
                "Android Keystore/KeyMint 暴露 security level/inside-secure-hardware 等信息。",
                "记录真实返回值与调用栈，不把任何单一安全级别自动判成异常。",
                "Android KeyInfo / KeyMint");

        add(DetectionRuleIds.PLAY_INTEGRITY_REQUEST, "integrity", "Play Integrity Token 请求",
                "应用请求 Play Integrity token，通常用于设备/应用/账号完整性验证。",
                "SPIC 等开源项目演示了 IntegrityManager 请求 token 的标准流程；最终 verdict 可能在服务端解析。",
                "记录 request 参数、时间和后续异常/退出；若看不到服务端 verdict，就只标记 CHECKED。",
                "Google Play Integrity / SPIC");
        add(DetectionRuleIds.PLAY_INTEGRITY_STANDARD_PREPARE, "integrity", "Standard Integrity 准备请求",
                "应用准备 Standard Integrity token provider。",
                "新式 Standard Integrity API 通常先 prepare，再由 provider 发起 token 请求。",
                "记录准备阶段及调用栈，不推断最终 verdict。",
                "Google Play Integrity Standard API");
        add(DetectionRuleIds.PLAY_INTEGRITY_STANDARD_REQUEST, "integrity", "Standard Integrity Token 请求",
                "应用通过 Standard Integrity provider 请求 token。",
                "该事件能证明 App 实际发起了完整性请求，但不能单独说明 verdict 通过或失败。",
                "结合返回异常、后续 token 读取和退出时序判断。",
                "Google Play Integrity Standard API");
        add(DetectionRuleIds.PLAY_INTEGRITY_TOKEN_QUERY, "integrity", "Play Integrity Token 读取",
                "应用读取 Integrity token 字符串，通常会随后发送到服务端。",
                "客户端通常看不到最终服务端 verdict；token 本身只证明请求已完成到客户端阶段。",
                "只记录 token 是否非空和长度，不记录完整 token 内容。",
                "Google Play Integrity");

        add(DetectionRuleIds.SELINUX_ACCESS_PROBE, "selinux", "DirtySepolicy access 探针",
                "应用访问 /sys/fs/selinux/access 或调用等价 policy access 检查，用于直接询问某条 SELinux 访问是否允许。",
                "DirtySepolicy 使用 SELinux policy 查询链来识别被修改/注入的策略状态。",
                "记录路径、上下文和时序；单次访问保持 CHECKED，连续 context→access→status/policyload 更有诊断价值。",
                "LSPosed DirtySepolicy");
        add(DetectionRuleIds.SELINUX_STATUS_SEQNO, "selinux", "SELinux status/seqno 查询",
                "应用读取 selinux status/sequence 以观察 policy reload 或状态变化。",
                "DirtySepolicy/SELinux 深度检测会结合 status 与 access/context 查询。",
                "记录具体调用链，不把读取行为本身视为 HIT。",
                "DirtySepolicy / SELinux status");
        add(DetectionRuleIds.SELINUX_POLICYLOAD_QUERY, "selinux", "SELinux policyload 查询",
                "应用访问 policyload 等节点观察 policy 重新加载状态。",
                "DirtySepolicy 类检测会把 policy 状态变化作为环境线索。",
                "仅作为 CHECKED 证据，与其他 SELinux 探针链联合分析。",
                "DirtySepolicy / SELinux policy");
        add(DetectionRuleIds.APP_ZYGOTE_PROBE, "selinux", "App-Zygote/隔离进程探针",
                "应用通过 isolated/app-zygote 进程行为检查 SELinux/运行环境差异。",
                "DirtySepolicy 公开实现利用 App Zygote 场景观察策略行为。",
                "只记录进程创建/隔离链和 SELinux 探针，不修改进程或策略。",
                "DirtySepolicy / Android App Zygote");

        add(DetectionRuleIds.PROCESS_FORK_QUERY, "zygisk", "fork/vfork 进程探针",
                "应用创建子进程后可能继续做 ptrace/waitpid 比较，用于反调试或 Zygisk 事件探测。",
                "DetectZygisk 使用 fork + ptrace + waitpid + PTRACE_GETEVENTMSG 组合。",
                "单独 fork 只算 CHECKED，只有形成完整序列并接近退出才提高归因。",
                "DetectZygisk / Linux process");
        add(DetectionRuleIds.PROCESS_WAITPID_QUERY, "zygisk", "waitpid 进程事件等待",
                "应用等待子进程/ptrace 事件，常用于反调试或 Zygisk 行为探针。",
                "DetectZygisk 的关键流程包含 waitpid。",
                "与 fork/ptrace request 同 TID/同时间窗口联合分析。",
                "DetectZygisk / ptrace");
        add(DetectionRuleIds.PTRACE_ATTACH_QUERY, "zygisk", "PTRACE_ATTACH",
                "应用主动 attach 目标进程，可能用于反调试自检或 Zygisk 行为探针。",
                "DetectZygisk 通过 ptrace attach 进入后续 event-message 检测。",
                "记录 target pid、返回值和调用者 SO，不修改 ptrace 行为。",
                "DetectZygisk");
        add(DetectionRuleIds.PTRACE_EVENTMSG_QUERY, "zygisk", "PTRACE_GETEVENTMSG",
                "应用读取 ptrace event message，是 DetectZygisk 一类流程的关键步骤。",
                "这比笼统记录 ptrace() 更能识别具体 Zygisk 探测链。",
                "与 fork/waitpid/PTRACE_ATTACH 序列关联，不单独判断设备状态。",
                "DetectZygisk");
        add(DetectionRuleIds.PTRACE_SYSCALL_QUERY, "debugger", "PTRACE_SYSCALL",
                "应用要求被跟踪进程在 syscall 边界暂停。",
                "常用于调试、反调试和 syscall 行为分析。",
                "仅记录 request/pid/result。",
                "Linux ptrace");
        add(DetectionRuleIds.PTRACE_DETACH_QUERY, "zygisk", "PTRACE_DETACH",
                "应用结束 ptrace 跟踪。",
                "与 attach/geteventmsg/waitpid 一起可以还原完整探针生命周期。",
                "仅记录行为与时序。",
                "DetectZygisk / Linux ptrace");

        add(DetectionRuleIds.APP_SIGNATURE_QUERY, "self_integrity", "自身签名/SigningInfo 查询",
                "应用读取自己的 signing certificate 或 package signing info，常用于自完整性与重打包检测。",
                "GarudaDefender 等 RASP 项目会验证签名与 APK 是否被重签名。",
                "记录查询目标、flags、调用栈和后续摘要计算；不修改签名数据。",
                "Android SigningInfo / GarudaDefender");
        add(DetectionRuleIds.SELF_APK_READ, "self_integrity", "自身 APK 读取",
                "应用直接打开自身 APK/BASE APK，可能用于计算摘要、检查资源或反篡改。",
                "自完整性方案常读取 base.apk 并检查 ZIP/签名/DEX 内容。",
                "记录路径、API、调用栈；普通资源读取只算 CHECKED。",
                "APK integrity / GarudaDefender");
        add(DetectionRuleIds.SELF_DEX_READ, "self_integrity", "DEX 完整性读取",
                "应用读取 classes*.dex，可能用于 checksum/代码完整性验证。",
                "RASP/防篡改项目会对 DEX 进行摘要或结构检查。",
                "记录具体 dex 路径与后续 MessageDigest 使用。",
                "DEX integrity / GarudaDefender");
        add(DetectionRuleIds.SELF_SO_READ, "self_integrity", "Native SO 完整性读取",
                "应用直接读取自身 native library，可能用于 ELF/哈希/Hook 完整性检查。",
                "Native RASP 常校验 SO 文件与内存映射。",
                "记录 SO 路径、调用者、后续摘要计算和退出链。",
                "Native integrity / GarudaDefender");
        add(DetectionRuleIds.CERTIFICATE_DIGEST_QUERY, "self_integrity", "证书/代码摘要计算",
                "应用通过 MessageDigest 对签名、APK、DEX 或 SO 数据计算摘要。",
                "摘要算法本身用途广泛，因此默认只算 CHECKED；需要与 signing/APK/DEX/SO 读取链联合判断。",
                "记录算法、输入长度和调用栈，不记录完整敏感内容。",
                "Java MessageDigest / app integrity");

        add(DetectionRuleIds.JAVA_LOAD_LIBRARY, "instrumentation", "Java Native 库加载",
                "应用通过 System.load/System.loadLibrary 加载 native 库；这能建立 Java 调用栈到 SO 的入口映射。",
                "这是诊断映射事件，不是安全检测命中。与 dlopen 事件按时间/TID 对齐后，可帮助定位 Java→JNI→SO 的调用关系。",
                "用于解释调用链，不作为安全风险归因项。",
                "Android Runtime / JNI loading");
        add(DetectionRuleIds.LINKER_DLOPEN, "instrumentation", "Native dlopen 库加载",
                "目标进程在 native 层动态加载 SO。",
                "ByteHook 的 dlopen callback 能观察后续加载的 ELF，并帮助把检测调用归到具体模块。",
                "用于模块映射和调用链解释，不作为安全风险归因项。",
                "ByteHook dlopen callback");
        add(DetectionRuleIds.LINKER_DLSYM, "instrumentation", "Native dlsym 符号解析",
                "目标进程解析 JNI_OnLoad、Java_*、RegisterNatives 或安全相关 native 符号。",
                "dlsym 记录可以补充 native 符号解析路径，但并不能覆盖所有 RegisterNatives 间接调用。",
                "用于 JNI/Linker 映射，不作为安全风险归因项。",
                "Android linker / ByteHook");

        add(DetectionRuleIds.JAVA_UNCAUGHT_EXCEPTION, "error", "Java 未捕获异常",
                "异常已经冒泡到线程顶层并进入 Thread 的未捕获异常分发路径。",
                "这是 Java Fatal 的强证据；YPower 只在原处理链之前记录 Throwable，不替换也不吞掉目标 App 的 Handler。",
                "优先查看异常类型、message、业务栈以及它前面的安全检测事件；修复真正抛出异常的业务/权限/状态问题。",
                "Android Thread / RuntimeInit");
        add(DetectionRuleIds.COROUTINE_UNHANDLED_EXCEPTION, "error", "Kotlin 协程未处理异常",
                "kotlinx.coroutines 将无法继续由普通协程传播路径处理的异常交给 CoroutineExceptionHandler。",
                "协程异常不一定导致进程退出；它只是异常传播层证据，后续如果同一个 Throwable 又进入 Java uncaught，可信度会显著提高。",
                "检查具体 CoroutineContext、Throwable 和调用栈；不要把它单独等同于进程 Crash。",
                "kotlinx.coroutines CoroutineExceptionHandler");
        add(DetectionRuleIds.RXJAVA2_GLOBAL_ERROR, "error", "RxJava2 全局错误",
                "RxJava2 将无法正常交付给下游的异步错误交给 RxJavaPlugins.onError。",
                "全局 RxJava error 不一定是 Fatal；它属于异步传播证据，需要结合后续 uncaught/exit 再判断。",
                "检查 Throwable、UndeliverableException 根因以及后续是否进入未捕获异常或主动退出。",
                "RxJava2 RxJavaPlugins");
        add(DetectionRuleIds.RXJAVA3_GLOBAL_ERROR, "error", "RxJava3 全局错误",
                "RxJava3 将无法正常交付给下游的异步错误交给 RxJavaPlugins.onError。",
                "全局 RxJava error 不一定是 Fatal；它属于异步传播证据，需要结合后续 uncaught/exit 再判断。",
                "检查 Throwable、UndeliverableException 根因以及后续是否进入未捕获异常或主动退出。",
                "RxJava3 RxJavaPlugins");

        add(DetectionRuleIds.JAVA_DEFAULT_EXCEPTION_HANDLER_SET, "instrumentation", "设置全局异常处理器",
                "目标 App 或第三方 SDK 设置了默认 Thread.UncaughtExceptionHandler。",
                "该事件用于解释 Crashlytics/Bugly/Sentry/自有 Handler 的崩溃处理链，不属于安全风险命中。",
                "仅用于调用链解释，不作为安全检测原因。",
                "Java Thread.UncaughtExceptionHandler");
        add(DetectionRuleIds.JAVA_THREAD_EXCEPTION_HANDLER_SET, "instrumentation", "设置线程异常处理器",
                "目标 App 为单独线程设置了 Thread.UncaughtExceptionHandler。",
                "该事件用于解释线程级崩溃处理链，不属于安全风险命中。",
                "仅用于调用链解释，不作为安全检测原因。",
                "Java Thread.UncaughtExceptionHandler");
        add(DetectionRuleIds.RXJAVA2_ERROR_HANDLER_SET, "instrumentation", "设置 RxJava2 全局错误处理器",
                "目标 App 或 SDK 安装了 RxJava2 全局 error handler。",
                "该事件用于说明异步错误最终可能被谁消费，不属于安全风险命中。",
                "仅用于异常传播解释。",
                "RxJava2 RxJavaPlugins");
        add(DetectionRuleIds.RXJAVA3_ERROR_HANDLER_SET, "instrumentation", "设置 RxJava3 全局错误处理器",
                "目标 App 或 SDK 安装了 RxJava3 全局 error handler。",
                "该事件用于说明异步错误最终可能被谁消费，不属于安全风险命中。",
                "仅用于异常传播解释。",
                "RxJava3 RxJavaPlugins");
    }

    private DetectionRuleCatalog() {}

    private static void addPackage(String id, String title, String target, String ref) {
        add(id, "package", title,
                "应用查询 " + target + " 相关包名，通常是为了识别已安装的环境管理器、Hook/调试工具或兼容性组件。",
                "PackageManager 查询只在真正返回目标包时算 HIT；NameNotFoundException 应视为 NOT_HIT。",
                "查看实际包名、返回结果和退出调用链；不要把单纯枚举动作等同于命中。",
                ref);
    }

    private static void addProperty(String id, String title, String why, String ref) {
        add(id, "environment", title, why,
                "系统属性属于环境信号，只有具体返回值满足风险条件时才应标记 HIT。",
                "核对实际 key/value，并通过同一操作复现确认它是否稳定紧邻退出。",
                ref);
    }

    private static void addCommand(String id, String title, String why) {
        add(id, "command", title, why,
                "仅观察到 Runtime.exec/ProcessBuilder 成功创建 Process，不能证明命令输出命中了风险条件。",
                "当前只标记 CHECKED；后续如果能关联 Process exit code/stdout，再升级为 HIT/NOT_HIT。",
                "Android Runtime / ProcessBuilder diagnostics");
    }

    private static void addExit(String id, String title) {
        add(id, "exit", title,
                "这是应用实际执行的退出终点。",
                "退出 API 是终点而不是前置原因，真正原因应从它前面的检测、异常和调用链中寻找。",
                "向前关联最高分的非退出规则，不把退出 API 本身当成根因。",
                "xCrash / YPower runtime timeline");
    }

    private static void add(
            String id,
            String category,
            String title,
            String whyDetected,
            String projectExplanation,
            String remediation,
            String reference
    ) {
        RULES.put(id, new DetectionRuleDefinition(
                id, category, title, whyDetected, projectExplanation, remediation, reference
        ));
    }

    public static DetectionRuleDefinition get(String id) {
        return RULES.get(id);
    }

    public static DetectionRuleDefinition getOrDefault(String id, String category, String title) {
        DetectionRuleDefinition rule = RULES.get(id);
        if (rule != null) return rule;
        return new DetectionRuleDefinition(
                id == null ? DetectionRuleIds.UNKNOWN : id,
                category == null ? "unknown" : category,
                title == null ? "未知检测" : title,
                "目标 App 在本次运行中执行了该项检查。",
                "当前规则库没有更具体的开源项目说明。",
                "查看原始参数、返回值、调用栈和退出时序后再决定是否需要处理。",
                "YPower runtime diagnostics"
        );
    }

    public static DetectionHitState evaluate(String ruleId, boolean observedPositive, String result, String exception) {
        String value = result == null ? "" : result.trim().toLowerCase(Locale.ROOT);

        if (isCheckedOnly(ruleId)) {
            return DetectionHitState.CHECKED;
        }

        if (isExit(ruleId) || isErrorEvent(ruleId)) {
            return DetectionHitState.HIT;
        }

        if (isBooleanRule(ruleId)) {
            if ("true".equals(value)) return DetectionHitState.HIT;
            if ("false".equals(value)) return DetectionHitState.NOT_HIT;
            return observedPositive ? DetectionHitState.HIT : DetectionHitState.UNKNOWN;
        }

        if (isPropertyRule(ruleId)) {
            return evaluateProperty(ruleId, value);
        }

        if (isPackageRule(ruleId)) {
            if (exception != null && exception.contains("NameNotFoundException")) {
                return DetectionHitState.NOT_HIT;
            }
            if (observedPositive) return DetectionHitState.HIT;
            return DetectionHitState.NOT_HIT;
        }

        if (isRootExistenceRule(ruleId)) {
            return observedPositive ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
        }

        if (DetectionRuleIds.PERMISSION_QUERY.equals(ruleId)) {
            if ("0".equals(value)) return DetectionHitState.NOT_HIT;
            if ("-1".equals(value)) return DetectionHitState.HIT;
            return DetectionHitState.UNKNOWN;
        }

        return observedPositive ? DetectionHitState.HIT : DetectionHitState.UNKNOWN;
    }

    private static boolean isCheckedOnly(String id) {
        return DetectionRuleIds.HOOK_PROC_MAPS.equals(id)
                || DetectionRuleIds.DEBUG_PROC_STATUS.equals(id)
                || DetectionRuleIds.MOUNT_PROC_MOUNT.equals(id)
                || DetectionRuleIds.ROOT_DATA_ADB.equals(id)
                || DetectionRuleIds.PACKAGE_ENUMERATION.equals(id)
                || DetectionRuleIds.CMD_SU.equals(id)
                || DetectionRuleIds.CMD_GETPROP.equals(id)
                || DetectionRuleIds.CMD_MOUNT.equals(id)
                || DetectionRuleIds.CMD_SELINUX.equals(id)
                || DetectionRuleIds.NATIVE_PTRACE.equals(id)
                || DetectionRuleIds.JAVA_LOAD_LIBRARY.equals(id)
                || DetectionRuleIds.LINKER_DLOPEN.equals(id)
                || DetectionRuleIds.LINKER_DLSYM.equals(id)
                || DetectionRuleIds.JAVA_DEFAULT_EXCEPTION_HANDLER_SET.equals(id)
                || DetectionRuleIds.JAVA_THREAD_EXCEPTION_HANDLER_SET.equals(id)
                || DetectionRuleIds.RXJAVA2_ERROR_HANDLER_SET.equals(id)
                || DetectionRuleIds.RXJAVA3_ERROR_HANDLER_SET.equals(id)
                || DetectionRuleIds.KERNEL_UNAME_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_PROC_VERSION.equals(id)
                || DetectionRuleIds.KERNEL_CMDLINE_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_OSRELEASE_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_SYS_VERSION_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_KPTR_QUERY.equals(id)
                || DetectionRuleIds.SELINUX_ENFORCE_READ.equals(id)
                || DetectionRuleIds.SELINUX_CONTEXT_READ.equals(id)
                || DetectionRuleIds.SELINUX_POLICY_READ.equals(id)
                || DetectionRuleIds.SELINUX_XATTR_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_SMAPS_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_FD_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_TASK_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_LINKER_ENUM_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_SIGNAL_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_VDSO_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_MPROTECT_QUERY.equals(id)
                || DetectionRuleIds.KEYSTORE_INSTANCE_QUERY.equals(id)
                || DetectionRuleIds.KEY_ATTESTATION_CHALLENGE.equals(id)
                || DetectionRuleIds.KEY_STRONGBOX_REQUEST.equals(id)
                || DetectionRuleIds.KEY_CERT_CHAIN_QUERY.equals(id)
                || DetectionRuleIds.KEY_SECURITY_LEVEL_QUERY.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_REQUEST.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_STANDARD_PREPARE.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_STANDARD_REQUEST.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_TOKEN_QUERY.equals(id)
                || DetectionRuleIds.SELINUX_ACCESS_PROBE.equals(id)
                || DetectionRuleIds.SELINUX_STATUS_SEQNO.equals(id)
                || DetectionRuleIds.SELINUX_POLICYLOAD_QUERY.equals(id)
                || DetectionRuleIds.APP_ZYGOTE_PROBE.equals(id)
                || DetectionRuleIds.PROCESS_FORK_QUERY.equals(id)
                || DetectionRuleIds.PROCESS_WAITPID_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_ATTACH_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_EVENTMSG_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_SYSCALL_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_DETACH_QUERY.equals(id)
                || DetectionRuleIds.APP_SIGNATURE_QUERY.equals(id)
                || DetectionRuleIds.SELF_APK_READ.equals(id)
                || DetectionRuleIds.SELF_DEX_READ.equals(id)
                || DetectionRuleIds.SELF_SO_READ.equals(id)
                || DetectionRuleIds.CERTIFICATE_DIGEST_QUERY.equals(id);
    }

    private static boolean isBooleanRule(String id) {
        return DetectionRuleIds.DEBUG_IS_CONNECTED.equals(id)
                || DetectionRuleIds.DEBUG_WAITING.equals(id);
    }

    private static boolean isRootExistenceRule(String id) {
        return DetectionRuleIds.ROOT_FILE_SU.equals(id)
                || DetectionRuleIds.ROOT_FILE_MAGISK.equals(id)
                || DetectionRuleIds.ROOT_FILE_KERNELSU.equals(id)
                || DetectionRuleIds.ROOT_FILE_APATCH.equals(id);
    }

    private static boolean isPackageRule(String id) {
        return id != null && id.startsWith("PACKAGE_")
                && !DetectionRuleIds.PACKAGE_ENUMERATION.equals(id);
    }

    private static boolean isPropertyRule(String id) {
        return id != null && id.startsWith("PROP_")
                && !DetectionRuleIds.PROP_GENERIC.equals(id);
    }

    private static boolean isExit(String id) {
        return id != null && id.startsWith("EXIT_");
    }

    private static boolean isErrorEvent(String id) {
        return DetectionRuleIds.JAVA_UNCAUGHT_EXCEPTION.equals(id)
                || DetectionRuleIds.COROUTINE_UNHANDLED_EXCEPTION.equals(id)
                || DetectionRuleIds.RXJAVA2_GLOBAL_ERROR.equals(id)
                || DetectionRuleIds.RXJAVA3_GLOBAL_ERROR.equals(id);
    }

    private static DetectionHitState evaluateProperty(String ruleId, String value) {
        if (value.isBlank()) return DetectionHitState.UNKNOWN;
        switch (ruleId) {
            case DetectionRuleIds.PROP_VERIFIED_BOOT:
                return "green".equals(value) ? DetectionHitState.NOT_HIT : DetectionHitState.HIT;
            case DetectionRuleIds.PROP_VBMETA_STATE:
                return ("unlocked".equals(value) || "orange".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_FLASH_LOCKED:
                return ("0".equals(value) || "false".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_DEBUGGABLE:
                return ("1".equals(value) || "true".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_SECURE:
                return ("0".equals(value) || "false".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_BUILD_TAGS:
                return value.contains("test-keys") ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_BUILD_TYPE:
                return ("eng".equals(value) || "userdebug".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            default:
                return DetectionHitState.UNKNOWN;
        }
    }
}
