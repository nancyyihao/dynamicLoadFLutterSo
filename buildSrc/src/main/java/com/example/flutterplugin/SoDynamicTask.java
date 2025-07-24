package com.example.flutterplugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.api.ApplicationVariant;
import com.example.flutterplugin.util.FileUtil;
import com.example.flutterplugin.util.HttpUtil;
import com.example.flutterplugin.util.LogUtil;
import com.example.flutterplugin.util.MD5Util;
import com.example.flutterplugin.util.SoType;
import com.example.flutterplugin.util.StringUtil;
import com.example.flutterplugin.util.ZipUtil;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

import javax.annotation.Nullable;

/**
 * 动态加载SO文件任务
 * 支持处理Flutter引擎SO和App SO文件
 */
public class SoDynamicTask extends DefaultTask {

    @Internal
    public ApplicationVariant variant;

    @Internal
    AppExtension appExtension;

    @Input
    public String mergeNativeLibsOutputPath;
    
    @Input
    public String soType; // "engine" 或 "app"
    
    @Internal
    public SoConfig soConfig; // SO配置信息
    
    @Internal
    public DynamicSoExtension dynamicSoExtension; // 动态SO扩展配置
    
    public SoConfig getSoConfig() {
        return soConfig;
    }
    
    public DynamicSoExtension getDynamicSoExtension() {
        return dynamicSoExtension;
    }

    public ApplicationVariant getVariant() {
        return variant;
    }

    public AppExtension getAppExtension() {
        return appExtension;
    }

    public String getMergeNativeLibsOutputPath() {
        return mergeNativeLibsOutputPath;
    }
    
    public String getSoType() {
        return soType;
    }

    public SoDynamicTask() {
        setGroup("flutterOpt");
    }

    @TaskAction
    public void optimizeSo() {
        LogUtil.log("开始处理SO，配置信息: " + (soConfig != null ? soConfig.toString() : "无配置"));
        
        // 统一处理SO文件
        String soVersion;
        String soName;
        String configFileName;
        SoType soTypeEnum;
        
        if ("engine".equals(soType)) {
            LogUtil.log("开始处理Flutter引擎SO文件");
            soVersion = findFlutterSDKVersion(getProject(), variant.getName());
            soName = "libflutter";
            configFileName = "flutterso.json";
            soTypeEnum = SoType.LIB_FLUTTER_SO;
        } else if ("app".equals(soType)) {
            LogUtil.log("开始处理App SO文件");
            soVersion = findAppSOVersion(getProject(), variant.getName());
            soName = "libapp";
            configFileName = "appso.json";
            soTypeEnum = SoType.LIB_APP_SO;
        } else {
            LogUtil.log("未知的SO类型: " + soType);
            return;
        }
        
        if (soVersion == null || soVersion.isEmpty()) {
            LogUtil.log("未找到" + soType + " SO版本，跳过处理");
            return;
        }
        LogUtil.log(soType + " SO版本: " + soVersion);
        
        // 处理SO文件
        processSoFiles(soVersion, soName, configFileName, soTypeEnum);
    }
    
    /**
     * 处理SO文件的通用方法
     * @param soVersion SO文件版本
     * @param soName SO文件名称（不含.so后缀）
     * @param configFileName 配置文件名称
     * @param soType SO类型
     */
    private void processSoFiles(String soVersion, String soName, String configFileName, SoType soType) {
        // 处理ARM架构的SO文件（移除x86支持）
        String[] abis = {"arm64-v8a", "armeabi-v7a"};
        java.util.Map<String, File> soFiles = new java.util.HashMap<>();
        
        // 收集所有架构的SO文件
        for (String abi : abis) {
            File soFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, soName + ".so");
            if (soFile != null && soFile.exists()) {
                LogUtil.log("找到 " + abi + " 架构的 " + soName + ".so: " + soFile.getAbsolutePath());
                soFiles.put(abi, soFile);
            }
        }
        
        if (soFiles.isEmpty()) {
            LogUtil.log("未找到任何架构的" + soName + ".so文件");
            return;
        }
        
        LogUtil.log("找到 " + soFiles.size() + " 个架构的" + soName + ".so文件");

        // 创建配置Map
        java.util.Map<String, Object> configMap = new java.util.HashMap<>();
        
        // 设置版本信息（去掉版本中的MD5，只保留基础版本号）
        String baseVersion = soVersion.split("-")[0]; // 去掉MD5部分
        configMap.put(soName + "Version", baseVersion);
        
        // 为每个架构处理SO文件
        boolean allArchsProcessed = true;
        
        for (java.util.Map.Entry<String, File> entry : soFiles.entrySet()) {
            String abi = entry.getKey();
            File soFile = entry.getValue();
            
            LogUtil.log("开始处理 " + abi + " 架构的 " + soName + ".so");
            
            // 计算MD5和文件大小
            String md5 = MD5Util.getFileMD5(soFile);
            long fileSize = soFile.length();
            LogUtil.log(abi + " " + soName + ".so MD5: " + md5 + ", 大小: " + fileSize + " bytes");
            
            // 检测该架构的SO是否需要重新上传
            String archSoUrl = checkSo(soType, soVersion + "-" + abi);
            if (archSoUrl != null && !archSoUrl.isEmpty()) {
                LogUtil.log(abi + " 架构的" + soName + ".so已存在于服务器，无需重新上传");
                // 即使不需要重新上传，也要添加架构信息到配置中
                addArchInfo(configMap, abi, archSoUrl, md5, fileSize);
                continue;
            }
            
            // 创建该架构的ZIP包（使用该架构SO文件的MD5）
            File zipFile = createSoZipPackage(soFile, soVersion, soName, abi, md5);
            if (zipFile == null) {
                LogUtil.log("创建 " + abi + " " + soName + ".so ZIP包失败");
                allArchsProcessed = false;
                continue;
            }
            
            // 上传ZIP包到本地服务器
            LogUtil.log("正在上传 " + abi + " " + soName + ".so ZIP包到本地服务器...");
            String url = HttpUtil.getInstance().upload(zipFile);
            if (url != null) {
                LogUtil.log(abi + " " + soName + ".so ZIP包上传成功: " + url);
                // 添加架构信息到配置中
                addArchInfo(configMap, abi, url, md5, fileSize);
                // 清理临时ZIP文件
                zipFile.delete();
            } else {
                LogUtil.log(abi + " " + soName + ".so上传失败");
                allArchsProcessed = false;
            }
        }
        
        if (allArchsProcessed) {
            // 所有架构都处理成功，删除APK中的SO文件
            for (java.util.Map.Entry<String, File> entry : soFiles.entrySet()) {
                String abi = entry.getKey();
                File soFile = entry.getValue();
                boolean deleteResult = soFile.delete();
                LogUtil.log("从APK中删除 " + abi + " " + soName + ".so结果= " + deleteResult);
            }
            LogUtil.log("所有架构的" + soName + ".so处理完成");
        } else {
            LogUtil.log("部分架构的" + soName + ".so处理失败，保留原始文件");
        }
        
        // 完成配置文件写入
        finalizeAssetsConfig(configMap, configFileName);
    }
    
    @Nullable
    private String checkSo(SoType soType, String version) {
        return HttpUtil.getInstance().check(soType, version);
    }
    
    private void addArchInfo(java.util.Map<String, Object> configMap, String arch, String url, String md5, long size) {
        java.util.Map<String, Object> archInfo = new java.util.HashMap<>();
        archInfo.put("url", url);
        archInfo.put("md5", md5);
        archInfo.put("size", size);
        configMap.put(arch, archInfo);
        LogUtil.log("添加架构信息: " + arch + " -> " + archInfo);
    }
    
    private void finalizeAssetsConfig(java.util.Map<String, Object> configMap, String configFileName) {
        try {
            // 使用配置中的版本信息，如果没有配置则使用默认值
            if (soConfig != null) {
                configMap.put("minAppVersion", soConfig.getMinVersion());
                configMap.put("maxAppVersion", soConfig.getMaxVersion());
                
                // 如果配置了上传和下载URL，也添加到配置中
                if (!soConfig.getUploadUrl().isEmpty()) {
                    configMap.put("uploadUrl", soConfig.getUploadUrl());
                }
                if (!soConfig.getDownloadUrl().isEmpty()) {
                    configMap.put("downloadUrl", soConfig.getDownloadUrl());
                }
                
                LogUtil.log("使用配置的版本范围: " + soConfig.getMinVersion() + " - " + soConfig.getMaxVersion());
            } else {
                // 添加默认值
                configMap.putIfAbsent("minAppVersion", "1.0.0");
                configMap.putIfAbsent("maxAppVersion", "9.9.9");
                LogUtil.log("使用默认版本范围: 1.0.0 - 9.9.9");
            }
            
            // 使用Gson直接转换Map为JSON
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String jsonContent = gson.toJson(configMap);
            
            // 直接使用默认的assets目录路径
            File assetsDir = new File(getProject().getProjectDir(), "src/main/assets");
            
            if (!assetsDir.exists()) {
                assetsDir.mkdirs();
            }
            
            File configFile = new File(assetsDir, configFileName);
            FileUtil.writeStringToFile(configFile, jsonContent);
            
            LogUtil.log("配置文件写入成功: " + configFile.getAbsolutePath());
            LogUtil.log("配置内容: " + jsonContent);
        } catch (Exception e) {
            LogUtil.log("配置文件写入失败: " + e.getMessage());
        }
    }
    
    private File createSoZipPackage(File soFile, String version, String packageName, String abi, String md5) {
        try {
            File tempDir = new File(getProject().getBuildDir(), "temp_so_packages");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            // 使用基础版本号和该架构SO文件的MD5来命名ZIP包
            String baseVersion = version.split("-")[0]; // 去掉版本中的MD5部分
            File zipFile = new File(tempDir, packageName + "_" + baseVersion + "-" + md5 + "-" + abi + ".zip");
            return ZipUtil.createSoPackage(soFile, version, zipFile, packageName, abi);
        } catch (Exception e) {
            LogUtil.log("创建 " + abi + " SO ZIP包失败: " + e.getMessage());
            return null;
        }
    }

    private String findAppSOVersion(Project project, String variantName) {
        Configuration configuration = project.getConfigurations().getByName(variantName + "RuntimeClasspath");
        for (ResolvedDependency resolvedDependency : configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies()) {
            //TODO: 修改成自己 flutter aar 的 ModuleGroup
            if (resolvedDependency.getModuleGroup().equals("com.example.flutter_module")) {
                return resolvedDependency.getModuleVersion();
            }
        }
        return "1.0.0";
    }
    
    private String findFlutterSDKVersion(Project project, String variantName) {
        Configuration configuration = project.getConfigurations().getByName(variantName + "RuntimeClasspath");
        for (ResolvedDependency resolvedDependency : configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies()) {
            if (resolvedDependency.getModuleGroup().equals("io.flutter")) {
                return resolvedDependency.getModuleVersion();
            }
        }
        return "1.0.0";
    }
}