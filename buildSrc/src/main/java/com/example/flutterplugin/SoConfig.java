package com.example.flutterplugin;

/**
 * SO配置类
 */
public class SoConfig {
    private final String name;
    private String minVersion = "1.0.0";
    private String maxVersion = "9.9.9";
    private String uploadUrl = "";
    private String downloadUrl = "";
    
    public SoConfig(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public String getMinVersion() {
        return minVersion;
    }
    
    public void setMinVersion(String minVersion) {
        this.minVersion = minVersion;
    }
    
    public void minVersion(String minVersion) {
        this.minVersion = minVersion;
    }
    
    public String getMaxVersion() {
        return maxVersion;
    }
    
    public void setMaxVersion(String maxVersion) {
        this.maxVersion = maxVersion;
    }
    
    public void maxVersion(String maxVersion) {
        this.maxVersion = maxVersion;
    }
    
    public String getUploadUrl() {
        return uploadUrl;
    }
    
    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }
    
    public void uploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
    
    public void downloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
    
    @Override
    public String toString() {
        return "SoConfig{" +
                "name='" + name + '\'' +
                ", minVersion='" + minVersion + '\'' +
                ", maxVersion='" + maxVersion + '\'' +
                ", uploadUrl='" + uploadUrl + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                '}';
    }
}