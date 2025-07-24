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
        
        // 处理libapp.so
        processAppSo(flutterSDKVersion);
        
        // 完成配置文件写入
        finalizeAssetsConfig();
        LogUtil.log("SO文件处理完成，配置已写入assets");
    }
    
    private void processFlutterSo(String flutterSDKVersion) {
        LogUtil.log("开始处理 libflutter.so");
        
        // 处理所有架构的libflutter.so
        String[] abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};
        File primaryFlutterSoFile = null;
        
        for (String abi : abis) {
            File flutterSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libflutter.so");
            if (flutterSoFile != null && flutterSoFile.exists()) {
                LogUtil.log("找到 " + abi + " 架构的 libflutter.so: " + flutterSoFile.getAbsolutePath());
                if (primaryFlutterSoFile == null) {
                    primaryFlutterSoFile = flutterSoFile; // 使用第一个找到的作为主要文件
                }
            }
        }
        
        if (primaryFlutterSoFile == null) {
            LogUtil.log("未找到任何架构的libflutter.so文件");
            return;
        }
        
        LogUtil.log("使用主要libflutter.so文件: " + primaryFlutterSoFile.getAbsolutePath());

        //检测 libflutter.so 是否需要重新上传
        String flutterSoUrl = checkFlutterSDK(flutterSDKVersion);
        if (flutterSoUrl != null && !flutterSoUrl.isEmpty()) {
            //不需要重新上传，直接写入配置并删除主要架构的文件
            LogUtil.log("libflutter.so已存在于服务器，无需重新上传");
            updateAssetsConfig("flutterSoUrl", flutterSoUrl);
            updateAssetsConfig("flutterSoVersion", flutterSDKVersion);
            updateAssetsConfig("flutterSoMd5", "");
            updateAssetsConfig("flutterSoSize", "0");
            
            // 删除所有架构的libflutter.so文件以实现完全动态加载
            for (String abi : abis) {
                File abiSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libflutter.so");
                if (abiSoFile != null && abiSoFile.exists()) {
                    boolean deleteResult = abiSoFile.delete();
                    LogUtil.log("删除" + abi + " libflutter.so结果= " + deleteResult);
                }
            }
            return;
        }
        
        //创建ZIP包并上传libflutter.so
        if (primaryFlutterSoFile == null || !primaryFlutterSoFile.exists()) {
            LogUtil.log("libflutter.so文件不存在，跳过处理");
            return;
        }
        
        // 计算MD5和文件大小
        String md5 = MD5Util.getFileMD5(primaryFlutterSoFile);
        long fileSize = primaryFlutterSoFile.length();
        LogUtil.log("libflutter.so MD5: " + md5 + ", 大小: " + fileSize + " bytes");
        
        // 创建ZIP包
        File zipFile = createSoZipPackage(primaryFlutterSoFile, flutterSDKVersion, "libflutter");
        if (zipFile == null) {
            LogUtil.log("创建libflutter.so ZIP包失败");
            return;
        }
        
        // 上传ZIP包到本地服务器
        LogUtil.log("正在上传libflutter.so ZIP包到本地服务器...");
        String url = HttpUtil.getInstance().upload(zipFile);
        if (url != null){
            LogUtil.log("libflutter.so ZIP包上传成功: " + url);
            updateAssetsConfig("flutterSoUrl", url);
            updateAssetsConfig("flutterSoVersion", flutterSDKVersion);
            updateAssetsConfig("flutterSoMd5", md5);
            updateAssetsConfig("flutterSoSize", String.valueOf(fileSize));
            
            // 删除所有架构的libflutter.so文件以实现完全动态加载
            for (String abi : abis) {
                File abiSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libflutter.so");
                if (abiSoFile != null && abiSoFile.exists()) {
                    boolean deleteResult = abiSoFile.delete();
                    LogUtil.log("从APK中删除" + abi + " libflutter.so结果= " + deleteResult);
                }
            }
            
            // 清理临时ZIP文件
            zipFile.delete();
            LogUtil.log("libflutter.so处理完成");
        } else {
            LogUtil.log("libflutter.so上传失败，保留原始文件");
        }
    }
    
    private void processAppSo(String version) {
        LogUtil.log("开始处理 libapp.so");
        
        // 处理所有架构的libapp.so
        String[] abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};
        File primaryAppSoFile = null;
        
        for (String abi : abis) {
            File appSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libapp.so");
            if (appSoFile != null && appSoFile.exists()) {
                LogUtil.log("找到 " + abi + " 架构的 libapp.so: " + appSoFile.getAbsolutePath());
                if (primaryAppSoFile == null) {
                    primaryAppSoFile = appSoFile; // 使用第一个找到的作为主要文件
                }
            }
        }
        
        if (primaryAppSoFile == null) {
            LogUtil.log("未找到任何架构的libapp.so文件");
            return;
        }
        
        LogUtil.log("使用主要libapp.so文件: " + primaryAppSoFile.getAbsolutePath());
        
        // 计算MD5和文件大小
        String md5 = MD5Util.getFileMD5(primaryAppSoFile);
        long fileSize = primaryAppSoFile.length();
        LogUtil.log("libapp.so MD5: " + md5 + ", 大小: " + fileSize + " bytes");
        
        // 检测 libapp.so 是否需要重新上传
        String appSoUrl = checkAppSo(md5);
        if (appSoUrl != null && !appSoUrl.isEmpty()) {
            LogUtil.log("libapp.so已存在于服务器，无需重新上传");
            updateAssetsConfig("appSoUrl", appSoUrl);
            updateAssetsConfig("appSoMd5", md5);
            updateAssetsConfig("appSoSize", String.valueOf(fileSize));
            
            // 删除所有架构的libapp.so文件以实现完全动态加载
            for (String abi : abis) {
                File abiAppSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libapp.so");
                if (abiAppSoFile != null && abiAppSoFile.exists()) {
                    boolean deleteResult = abiAppSoFile.delete();
                    LogUtil.log("删除" + abi + " libapp.so结果= " + deleteResult);
                }
            }
            return;
        }
        
        // 创建ZIP包
        File zipFile = createSoZipPackage(primaryAppSoFile, version, "libapp");
        if (zipFile == null) {
            LogUtil.log("创建libapp.so ZIP包失败");
            return;
        }
        
        // 上传ZIP包到本地服务器
        LogUtil.log("正在上传libapp.so ZIP包到本地服务器...");
        String url = HttpUtil.getInstance().upload(zipFile);
        if (url != null){
            LogUtil.log("libapp.so ZIP包上传成功: " + url);
            updateAssetsConfig("appSoUrl", url);
            updateAssetsConfig("appSoMd5", md5);
            updateAssetsConfig("appSoSize", String.valueOf(fileSize));
            
            // 删除所有架构的libapp.so文件以实现完全动态加载
            for (String abi : abis) {
                File abiAppSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libapp.so");
                if (abiAppSoFile != null && abiAppSoFile.exists()) {
                    boolean deleteResult = abiAppSoFile.delete();
                    LogUtil.log("从APK中删除" + abi + " libapp.so结果= " + deleteResult);
                }
            }
            
            // 清理临时ZIP文件
            zipFile.delete();
            LogUtil.log("libapp.so处理完成");
        } else {
            LogUtil.log("libapp.so上传失败，保留原始文件");
        }
        
        // 检查是否需要禁用strip任务
        File arm64FlutterSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, "arm64-v8a", "libflutter.so");
        File arm64AppSoFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, "arm64-v8a", "libapp.so");
        if ((arm64FlutterSoFile == null || !arm64FlutterSoFile.exists()) && 
            (arm64AppSoFile == null || !arm64AppSoFile.exists())) {
            disableStripTasks();
            LogUtil.log("arm64-v8a架构的SO文件已删除，已禁用strip任务");
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
    private java.util.Map<String, String> configMap = new java.util.HashMap<>();
    
    private void updateAssetsConfig(String key, String value) {
        configMap.put(key, value);
        LogUtil.log("添加配置: " + key + " = " + value);
    }
    
    private void finalizeAssetsConfig() {
        // 构建完整的JSON配置
        StringBuilder jsonBuilder = new StringBuilder("{");
        
        // 添加默认值
        configMap.putIfAbsent("minAppVersion", "1.0.0");
        configMap.putIfAbsent("maxAppVersion", "2.0.0");
        
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : configMap.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        
        jsonBuilder.append("}");
        
        try {
            // 直接写入文件
            File configDir = new File(getProject().getBuildDir(), "soConfig");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            File configFile = new File(configDir, "flutterso.json");
            FileUtil.writeStringToFile(configFile, jsonBuilder.toString());
            
            // 动态添加asset目录
            AndroidSourceSet mainSourceSet = appExtension.getSourceSets().getByName("main");
            mainSourceSet.getAssets().srcDirs(configDir.getAbsolutePath());
            
            LogUtil.log("配置文件写入成功: " + configFile.getAbsolutePath());
            LogUtil.log("配置内容: " + jsonBuilder.toString());
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
