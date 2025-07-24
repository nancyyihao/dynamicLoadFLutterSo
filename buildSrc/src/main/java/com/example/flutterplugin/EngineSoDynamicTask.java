package com.example.flutterplugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.api.dsl.AndroidSourceSet;
import com.example.flutterplugin.util.*;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

import javax.annotation.Nullable;

public class EngineSoDynamicTask extends DefaultTask {

    @Internal
    public ApplicationVariant variant;

    @Internal
    AppExtension appExtension;

    @Input
    public String mergeNativeLibsOutputPath;


    public ApplicationVariant getVariant() {
        return variant;
    }

    public AppExtension getAppExtension() {
        return appExtension;
    }

    public String getMergeNativeLibsOutputPath() {
        return mergeNativeLibsOutputPath;
    }


    public EngineSoDynamicTask() {
        setGroup("flutterOpt");
    }

    @TaskAction
    public void optimizeEngineSo() {
        String flutterSDKVersion = findFlutterSDKVersion(getProject(), variant.getName());
        if (flutterSDKVersion == null || flutterSDKVersion.isEmpty()) return;
        LogUtil.log("Flutter SDK version is " + flutterSDKVersion);

        // 处理libflutter.so
        processFlutterSo(flutterSDKVersion);
        
        // 完成配置文件写入
        finalizeAssetsConfig();
        LogUtil.log("SO文件处理完成，配置已写入assets");
    }
    
    private void processFlutterSo(String flutterSDKVersion) {
        LogUtil.log("开始处理 libflutter.so");
        
        // 处理ARM架构的libflutter.so（移除x86支持）
        String[] abis = {"arm64-v8a", "armeabi-v7a"};
        java.util.Map<String, File> flutterSoFiles = new java.util.HashMap<>();
        
        // 收集所有架构的libflutter.so文件
        for (String abi : abis) {
            File flutterSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libflutter.so");
            if (flutterSoFile != null && flutterSoFile.exists()) {
                LogUtil.log("找到 " + abi + " 架构的 libflutter.so: " + flutterSoFile.getAbsolutePath());
                flutterSoFiles.put(abi, flutterSoFile);
            }
        }
        
        if (flutterSoFiles.isEmpty()) {
            LogUtil.log("未找到任何架构的libflutter.so文件");
            return;
        }
        
        LogUtil.log("找到 " + flutterSoFiles.size() + " 个架构的libflutter.so文件");

        // 先设置版本信息（去掉版本中的MD5，只保留基础版本号）
        String baseVersion = flutterSDKVersion.split("-")[0]; // 去掉MD5部分
        updateAssetsConfig("flutterSoVersion", baseVersion);
        
        // 为每个架构处理libflutter.so
        boolean allArchsProcessed = true;
        
        for (java.util.Map.Entry<String, File> entry : flutterSoFiles.entrySet()) {
            String abi = entry.getKey();
            File flutterSoFile = entry.getValue();
            
            LogUtil.log("开始处理 " + abi + " 架构的 libflutter.so");
            
            // 计算MD5和文件大小
            String md5 = MD5Util.getFileMD5(flutterSoFile);
            long fileSize = flutterSoFile.length();
            LogUtil.log(abi + " libflutter.so MD5: " + md5 + ", 大小: " + fileSize + " bytes");
            
            // 检测该架构的SO是否需要重新上传
            String archFlutterSoUrl = checkFlutterSDK(flutterSDKVersion + "-" + abi);
            if (archFlutterSoUrl != null && !archFlutterSoUrl.isEmpty()) {
                LogUtil.log(abi + " 架构的libflutter.so已存在于服务器，无需重新上传");
                // 即使不需要重新上传，也要添加架构信息到配置中
                addArchInfo(abi, archFlutterSoUrl, md5, fileSize);
                continue;
            }
            
            // 创建该架构的ZIP包（去掉文件名中的MD5）
            File zipFile = createSoZipPackage(flutterSoFile, flutterSDKVersion, "libflutter", abi);
            if (zipFile == null) {
                LogUtil.log("创建 " + abi + " libflutter.so ZIP包失败");
                allArchsProcessed = false;
                continue;
            }
            
            // 上传ZIP包到本地服务器
            LogUtil.log("正在上传 " + abi + " libflutter.so ZIP包到本地服务器...");
            String url = HttpUtil.getInstance().upload(zipFile);
            if (url != null) {
                LogUtil.log(abi + " libflutter.so ZIP包上传成功: " + url);
                // 添加架构信息到配置中
                addArchInfo(abi, url, md5, fileSize);
                // 清理临时ZIP文件
                zipFile.delete();
            } else {
                LogUtil.log(abi + " libflutter.so上传失败");
                allArchsProcessed = false;
            }
        }
        
        if (allArchsProcessed) {
            // 所有架构都处理成功，删除APK中的SO文件
            for (java.util.Map.Entry<String, File> entry : flutterSoFiles.entrySet()) {
                String abi = entry.getKey();
                File flutterSoFile = entry.getValue();
                boolean deleteResult = flutterSoFile.delete();
                LogUtil.log("从APK中删除 " + abi + " libflutter.so结果= " + deleteResult);
            }
            LogUtil.log("所有架构的libflutter.so处理完成");
        } else {
            LogUtil.log("部分架构的libflutter.so处理失败，保留原始文件");
        }
    }
    
    @Nullable
    private String checkFlutterSDK(String sdkVersion) {
        return HttpUtil.getInstance().check(SoType.LIB_FLUTTER_SO, sdkVersion);
    }
    
    @Nullable
    private String checkAppSo(String md5) {
        return HttpUtil.getInstance().check(SoType.LIB_APP_SO, md5);
    }

    private void write2Assets(String version, String url, String md5, long size) {
        String content = "\"flutterSoUrl\":\"" + url + "\",\"flutterSoVersion\":\"" + version + 
                        "\",\"flutterSoMd5\":\"" + md5 + "\",\"flutterSoSize\":" + size;
        Write2AssetsUtil.getInstance().writeContent(content);
    }
    
    // 用于收集配置信息的Map
    private java.util.Map<String, Object> configMap = new java.util.HashMap<>();
    
    private void updateAssetsConfig(String key, String value) {
        configMap.put(key, value);
        LogUtil.log("添加配置: " + key + " = " + value);
    }
    
    private void addArchInfo(String arch, String url, String md5, long size) {
        java.util.Map<String, Object> archInfo = new java.util.HashMap<>();
        archInfo.put("url", url);
        archInfo.put("md5", md5);
        archInfo.put("size", size);
        configMap.put(arch, archInfo);
        LogUtil.log("添加架构信息: " + arch + " -> " + archInfo);
    }
    
    private void finalizeAssetsConfig() {
        try {
            // 添加默认值
            configMap.putIfAbsent("minAppVersion", "1.0.0");
            configMap.putIfAbsent("maxAppVersion", "2.0.0");
            
            // 使用Gson直接转换Map为JSON
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String jsonContent = gson.toJson(configMap);
            
            // 直接使用默认的assets目录路径
            File assetsDir = new File(getProject().getProjectDir(), "src/main/assets");
            
            if (!assetsDir.exists()) {
                assetsDir.mkdirs();
            }
            
            File configFile = new File(assetsDir, "flutterso.json");
            FileUtil.writeStringToFile(configFile, jsonContent);
            
            LogUtil.log("配置文件写入成功: " + configFile.getAbsolutePath());
            LogUtil.log("配置内容: " + jsonContent);
        } catch (Exception e) {
            LogUtil.log("配置文件写入失败: " + e.getMessage());
        }
    }
    
    private File createSoZipPackage(File soFile, String version, String packageName) {
        try {
            File tempDir = new File(getProject().getBuildDir(), "temp_so_packages");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            File zipFile = new File(tempDir, packageName + "_" + version + ".zip");
            return ZipUtil.createSoPackage(soFile, version, zipFile, packageName);
        } catch (Exception e) {
            LogUtil.log("创建SO ZIP包失败: " + e.getMessage());
            return null;
        }
    }
    
    private File createSoZipPackage(File soFile, String version, String packageName, String abi) {
        try {
            File tempDir = new File(getProject().getBuildDir(), "temp_so_packages");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            File zipFile = new File(tempDir, packageName + "_" + version + "-" + abi + ".zip");
            return ZipUtil.createSoPackage(soFile, version, zipFile, packageName, abi);
        } catch (Exception e) {
            LogUtil.log("创建 " + abi + " SO ZIP包失败: " + e.getMessage());
            return null;
        }
    }

    private String findFlutterSDKVersion(Project project, String variantName) {
        Configuration configuration = project.getConfigurations().getByName(variantName + "RuntimeClasspath");
        for (ResolvedDependency resolvedDependency : configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies()) {
            if (resolvedDependency.getModuleGroup().equals("io.flutter")) {
                return resolvedDependency.getModuleVersion();
            }
        }
        return null;
    }
    
    private void disableStripTasks() {
        try {
            String variantName = variant.getName();
            String capitalizedVariantName = variantName.substring(0, 1).toUpperCase() + variantName.substring(1);
            
            // 禁用strip相关任务
            String[] stripTaskNames = {
                "strip" + capitalizedVariantName + "DebugSymbols",
                "extractNativeDebugMetadata" + capitalizedVariantName
            };
            
            for (String taskName : stripTaskNames) {
                try {
                    org.gradle.api.Task stripTask = getProject().getTasks().findByName(taskName);
                    if (stripTask != null) {
                        stripTask.setEnabled(false);
                        LogUtil.log("已禁用任务: " + taskName);
                    }
                } catch (Exception e) {
                    LogUtil.log("禁用任务失败 " + taskName + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LogUtil.log("禁用strip任务时出错: " + e.getMessage());
        }
    }
}
