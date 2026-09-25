package com.yagay.ypower.data;

import com.yagay.ypower.model.RecommendedAppPreset;

import java.util.List;

public final class RecommendedAppRegistry {
    private RecommendedAppRegistry() {}

    private static RecommendedAppPreset diagnosticMedia(String pkg, String name, String reason) {
        return new RecommendedAppPreset(
                pkg, name, reason,
                true, true, true, true,
                false, false,
                true, true, true, true, true, true, true, true
        );
    }

    public static final List<RecommendedAppPreset> BUILTIN = List.of(
            diagnosticMedia(
                    "com.ss.android.ugc.aweme",
                    "抖音",
                    "适合做环境/退出诊断；默认只记录敏感检测行为，不修改检测结果。"
            ),
            diagnosticMedia(
                    "com.ss.android.ugc.aweme.lite",
                    "抖音极速版",
                    "适合做环境/退出诊断；默认只记录敏感检测行为，不修改检测结果。"
            ),
            diagnosticMedia(
                    "com.phoenix.read",
                    "红果免费短剧",
                    "适合分析后台、环境检测和主动退出链；默认只启用诊断型 Hook。"
            ),
            diagnosticMedia(
                    "com.phoenix.read.oversea.gp",
                    "红果短剧（海外版）",
                    "适合分析后台、环境检测和主动退出链；默认只启用诊断型 Hook。"
            )
    );

    public static RecommendedAppPreset find(String packageName) {
        for (RecommendedAppPreset preset : BUILTIN) {
            if (preset.packageName.equals(packageName)) return preset;
        }
        return null;
    }
}
