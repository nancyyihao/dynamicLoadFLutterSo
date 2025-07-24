package com.example.flutterplugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.api.ApplicationVariant;
import com.example.flutterplugin.util.*;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class AppSoDynamicTask extends DefaultTask {

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


    public AppSoDynamicTask() {
        setGroup("flutterOpt");
    }

    @TaskAction
    public void optimizeEngineSo() {
        String appSOVersion = findAppSOVersion(getProject(), variant.getName());
        if (appSOVersion == null || appSOVersion.isEmpty()) return;
        LogUtil.log("libapp.so version is " + appSOVersion);

        String appSoUrl = checkFlutterSDK(appSOVersion);
        
        // 查找所有架构的libapp.so文件
        String[] abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};
        File primarySoFile = null;
        
        for (String abi : abis) {
            File soFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libapp.so");
            if (soFile != null && soFile.exists()) {
                LogUtil.log("找到 " + abi + " 架构的 libapp.so: " + soFile.getAbsolutePath());
                if (primarySoFile == null) {
                    primarySoFile = soFile; // 使用第一个找到的作为主要文件
                }
            }
        }
        
        if (primarySoFile == null) {
            LogUtil.log("未找到任何架构的libapp.so文件");
            return;
        }

        if (appSoUrl != null && !appSoUrl.isEmpty()) {
            //不需要重新上传，直接写入。并删除所有架构的文件
            write2Assets(appSOVersion, appSoUrl, "", 0);
            for (String abi : abis) {
                File soFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libapp.so");
                if (soFile != null && soFile.exists()) {
                    boolean deleteResult = soFile.delete();
                    LogUtil.log("删除" + abi + " libapp.so结果= " + deleteResult);
                }
            }
            return;
        }
        
        //创建ZIP包并上传
        if (primarySoFile == null || !primarySoFile.exists()) return;
        
        // 计算MD5和文件大小
        String md5 = MD5Util.getFileMD5(primarySoFile);
        long fileSize = primarySoFile.length();
        
        // 创建ZIP包
        File zipFile = createSoZipPackage(primarySoFile, appSOVersion, "libapp");
        if (zipFile == null) {
            LogUtil.log("创建ZIP包失败");
            return;
        }
        
        // 上传ZIP包到本地服务器
        LogUtil.log("正在上传App SO包到本地服务器...");
        String url = HttpUtil.getInstance().upload(zipFile);
        if (url != null){
            LogUtil.log("App SO包上传成功: " + url);
            write2Assets(appSOVersion, url, md5, fileSize);
            
            // 删除所有架构的libapp.so文件
            for (String abi : abis) {
                File soFile = FileUtil.findSpecificFile(mergeNativeLibsOutputPath, abi, "libapp.so");
                if (soFile != null && soFile.exists()) {
                    boolean deleteResult = soFile.delete();
                    LogUtil.log("从APK中删除" + abi + " libapp.so结果= " + deleteResult);
                }
            }
            
            // 清理临时ZIP文件
            zipFile.delete();
            LogUtil.log("App SO包已成功上传到本地服务器，APK中的所有架构SO文件已移除");
        } else {
            LogUtil.log("上传到本地服务器失败，保留原始SO文件");
        }
    }

    private String checkFlutterSDK(String sdkVersion) {
        return HttpUtil.getInstance().check(SoType.LIB_APP_SO, sdkVersion);
    }

    private void write2Assets(String version, String url, String md5, long size) {
        String content = "\"appSoUrl\":\"" + url + "\",\"appSoVersion\":\"" + version + 
                        "\",\"appSoMd5\":\"" + md5 + "\",\"appSoSize\":" + size + 
                        ",\"minAppVersion\":\"1.0.0\",\"maxAppVersion\":\"9.9.9\"";
        Write2AssetsUtil.getInstance().writeContent(content).endWrite();
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

    private String findAppSOVersion(Project project, String variantName) {
        Configuration configuration = project.getConfigurations().getByName(variantName + "RuntimeClasspath");
        for (ResolvedDependency resolvedDependency : configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies()) {
            //TODO: 修改成自己 flutter aar 的 ModuleGroup
            if (resolvedDependency.getModuleGroup().equals("com.stefan.flutter_module")) {
                return resolvedDependency.getModuleVersion();
            }
        }
        return null;
    }
}