package com.yagay.ypower.util;

public final class ShellEscaper {
    private ShellEscaper() {}

    public static String q(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
