package com.yagay.ypower.hook;

import java.util.Locale;

public final class DetectionRuleIds {
    private DetectionRuleIds() {}

    public static final String UNKNOWN = "UNKNOWN";
    public static final String ROOT_FILE_SU = "ROOT_FILE_SU";
    public static final String ROOT_FILE_MAGISK = "ROOT_FILE_MAGISK";
    public static final String ROOT_FILE_KERNELSU = "ROOT_FILE_KERNELSU";
    public static final String ROOT_FILE_APATCH = "ROOT_FILE_APATCH";
    public static final String ROOT_DATA_ADB = "ROOT_DATA_ADB";
    public static final String HOOK_PROC_MAPS = "HOOK_PROC_MAPS";
    public static final String DEBUG_PROC_STATUS = "DEBUG_PROC_STATUS";
    public static final String MOUNT_PROC_MOUNT = "MOUNT_PROC_MOUNT";
    public static final String PACKAGE_MAGISK = "PACKAGE_MAGISK";
    public static final String PACKAGE_KERNELSU = "PACKAGE_KERNELSU";
    public static final String PACKAGE_APATCH = "PACKAGE_APATCH";
    public static final String PACKAGE_LSPOSED = "PACKAGE_LSPOSED";
    public static final String PACKAGE_XPOSED = "PACKAGE_XPOSED";
    public static final String PACKAGE_FRIDA = "PACKAGE_FRIDA";
    public static final String PACKAGE_SHIZUKU = "PACKAGE_SHIZUKU";
    public static final String PACKAGE_ENUMERATION = "PACKAGE_ENUMERATION";
    public static final String PROP_VERIFIED_BOOT = "PROP_VERIFIED_BOOT";
    public static final String PROP_VBMETA_STATE = "PROP_VBMETA_STATE";
    public static final String PROP_FLASH_LOCKED = "PROP_FLASH_LOCKED";
    public static final String PROP_DEBUGGABLE = "PROP_DEBUGGABLE";
    public static final String PROP_SECURE = "PROP_SECURE";
    public static final String PROP_BUILD_TAGS = "PROP_BUILD_TAGS";
    public static final String PROP_BUILD_TYPE = "PROP_BUILD_TYPE";
    public static final String PROP_GENERIC = "PROP_GENERIC";
    public static final String CMD_SU = "CMD_SU";
    public static final String CMD_GETPROP = "CMD_GETPROP";
    public static final String CMD_MOUNT = "CMD_MOUNT";
    public static final String CMD_SELINUX = "CMD_SELINUX";
    public static final String DEBUG_IS_CONNECTED = "DEBUG_IS_CONNECTED";
    public static final String DEBUG_WAITING = "DEBUG_WAITING";
    public static final String PERMISSION_QUERY = "PERMISSION_QUERY";
    public static final String EXIT_SYSTEM = "EXIT_SYSTEM";
    public static final String EXIT_HALT = "EXIT_HALT";
    public static final String EXIT_KILL_PROCESS = "EXIT_KILL_PROCESS";

    public static String forPath(String value) {
        String s = lower(value);
        if (s.contains("/proc/self/maps")) return HOOK_PROC_MAPS;
        if (s.contains("/proc/self/status")) return DEBUG_PROC_STATUS;
        if (s.contains("mountinfo") || s.contains("/proc/mount")) return MOUNT_PROC_MOUNT;
        if (s.contains("/data/adb")) return ROOT_DATA_ADB;
        if (s.contains("magisk")) return ROOT_FILE_MAGISK;
        if (s.contains("kernelsu") || s.contains("/data/adb/ksu")) return ROOT_FILE_KERNELSU;
        if (s.contains("apatch") || s.contains("/data/adb/ap")) return ROOT_FILE_APATCH;
        if (s.endsWith("/su") || s.contains("/system/bin/su") || s.contains("/system/xbin/su")) {
            return ROOT_FILE_SU;
        }
        return UNKNOWN;
    }

    public static String forPackage(String value) {
        String s = lower(value);
        if (s.contains("magisk")) return PACKAGE_MAGISK;
        if (s.contains("kernelsu")) return PACKAGE_KERNELSU;
        if (s.contains("apatch")) return PACKAGE_APATCH;
        if (s.contains("lsposed")) return PACKAGE_LSPOSED;
        if (s.contains("xposed")) return PACKAGE_XPOSED;
        if (s.contains("frida")) return PACKAGE_FRIDA;
        if (s.contains("shizuku")) return PACKAGE_SHIZUKU;
        return UNKNOWN;
    }

    public static String forProperty(String value) {
        String s = lower(value);
        if (s.contains("verifiedbootstate")) return PROP_VERIFIED_BOOT;
        if (s.contains("vbmeta.device_state")) return PROP_VBMETA_STATE;
        if (s.contains("flash.locked")) return PROP_FLASH_LOCKED;
        if (s.contains("ro.debuggable")) return PROP_DEBUGGABLE;
        if (s.contains("ro.secure")) return PROP_SECURE;
        if (s.contains("ro.build.tags")) return PROP_BUILD_TAGS;
        if (s.contains("ro.build.type")) return PROP_BUILD_TYPE;
        return PROP_GENERIC;
    }

    public static String forCommand(String value) {
        String s = lower(value);
        if (s.contains("which su") || s.matches(".*(^|\\s|/)su(\\s|$).*")) return CMD_SU;
        if (s.contains("getprop")) return CMD_GETPROP;
        if (s.contains("mount")) return CMD_MOUNT;
        if (s.contains("getenforce")) return CMD_SELINUX;
        return UNKNOWN;
    }

    public static boolean propertyValueLooksMatched(String ruleId, String result) {
        String s = lower(result).trim();
        switch (ruleId) {
            case PROP_VERIFIED_BOOT:
                return !s.isEmpty() && !"green".equals(s);
            case PROP_VBMETA_STATE:
                return "unlocked".equals(s) || "orange".equals(s);
            case PROP_FLASH_LOCKED:
                return "0".equals(s) || "false".equals(s);
            case PROP_DEBUGGABLE:
                return "1".equals(s) || "true".equals(s);
            case PROP_SECURE:
                return "0".equals(s) || "false".equals(s);
            case PROP_BUILD_TAGS:
                return s.contains("test-keys");
            default:
                return false;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
