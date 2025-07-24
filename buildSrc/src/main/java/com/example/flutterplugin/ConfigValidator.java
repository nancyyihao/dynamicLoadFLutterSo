package com.example.flutterplugin;

import com.example.flutterplugin.util.LogUtil;

/**
 * 配置验证工具类
 */
public class ConfigValidator {
    
    /**
     * 验证SO配置的有效性
     */
    public static boolean validateSoConfig(SoConfig config) {
        if (config == null) {
            LogUtil.log("❌ SO配置为空");
            return false;
        }
        
        // 验证版本号格式
        if (!isValidVersion(config.getMinVersion())) {
            LogUtil.log("❌ 最小版本号格式无效: " + config.getMinVersion());
            return false;
        }
        
        if (!isValidVersion(config.getMaxVersion())) {
            LogUtil.log("❌ 最大版本号格式无效: " + config.getMaxVersion());
            return false;
        }
        
        // 验证版本范围
        if (compareVersions(config.getMinVersion(), config.getMaxVersion()) > 0) {
            LogUtil.log("❌ 最小版本不能大于最大版本: " + config.getMinVersion() + " > " + config.getMaxVersion());
            return false;
        }
        
        LogUtil.log("✅ SO配置验证通过: " + config.getName());
        return true;
    }
    
    /**
     * 验证版本号格式是否有效
     */
    private static boolean isValidVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return false;
        }
        
        // 简单的版本号格式验证：x.y.z
        String versionPattern = "^\\d+\\.\\d+\\.\\d+$";
        return version.matches(versionPattern);
    }
    
    /**
     * 比较两个版本号
     * @return 负数表示v1 < v2，0表示相等，正数表示v1 > v2
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        
        return 0;
    }
    
    /**
     * 验证URL格式
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return true; // 空URL是允许的
        }
        
        return url.startsWith("http://") || url.startsWith("https://");
    }
}