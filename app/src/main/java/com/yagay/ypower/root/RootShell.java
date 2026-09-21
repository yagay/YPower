package com.yagay.ypower.root;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.List;

public final class RootShell {
    private RootShell() {}

    public static boolean isRootAvailable() {
        try {
            return Shell.getShell().isRoot();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static CommandResult exec(String... commands) {
        try {
            Shell.Result r = Shell.cmd(commands).exec();
            return new CommandResult(r.getCode(), new ArrayList<>(r.getOut()), new ArrayList<>(r.getErr()));
        } catch (Throwable t) {
            List<String> err = new ArrayList<>();
            err.add(t.toString());
            return new CommandResult(-1, new ArrayList<>(), err);
        }
    }

    public static final class CommandResult {
        public final int code;
        public final List<String> out;
        public final List<String> err;

        public CommandResult(int code, List<String> out, List<String> err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }

        public boolean ok() { return code == 0; }

        public String text() {
            StringBuilder b = new StringBuilder();
            for (String s : out) b.append(s).append('\n');
            for (String s : err) b.append(s).append('\n');
            return b.toString().trim();
        }
    }
}
