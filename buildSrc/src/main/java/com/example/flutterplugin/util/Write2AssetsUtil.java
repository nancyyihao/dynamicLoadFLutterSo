package com.example.flutterplugin.util;

import com.android.build.api.dsl.AndroidSourceSet;
import com.android.build.gradle.AppExtension;

import java.io.File;
import java.io.IOException;

public class Write2AssetsUtil {

    private StringBuilder stringBuilder;
    private AppExtension appExtension;
    private File flutterSOConfigFile;
    private boolean initialized = false;

    private Write2AssetsUtil(){}

    private static volatile Write2AssetsUtil singleton;

    public static Write2AssetsUtil getInstance(){
        if(singleton == null){
            synchronized (Write2AssetsUtil.class){
                if(singleton == null){
                    singleton = new Write2AssetsUtil();
                }
            }
        }
        return singleton;
    }

    public void init(AppExtension appExtension, String parentPath){
        this.appExtension = appExtension;
        flutterSOConfigFile = new File(parentPath + File.separator + "soConfig", "flutterso.json");
        // 重置StringBuilder
        stringBuilder = new StringBuilder("{");
        initialized = true;
        LogUtil.log("Write2AssetsUtil初始化完成: " + flutterSOConfigFile.getAbsolutePath());
    }

    public Write2AssetsUtil writeContent(String content){
        if (!initialized) {
            LogUtil.log("Write2AssetsUtil未初始化，跳过写入");
            return this;
        }
        
        if (stringBuilder.length() > 1) { // 如果不是第一个元素，添加逗号
            stringBuilder.append(",");
        }
        stringBuilder.append(content);
        LogUtil.log("添加配置内容: " + content);
        return this;
    }

    public void endWrite(){
        if (!initialized) {
            LogUtil.log("Write2AssetsUtil未初始化，跳过结束写入");
            return;
        }
        
        stringBuilder.append("}");
        try {
            // 确保目录存在
            if (!flutterSOConfigFile.getParentFile().exists()) {
                flutterSOConfigFile.getParentFile().mkdirs();
            }
            
            FileUtil.writeStringToFile(flutterSOConfigFile, stringBuilder.toString());
            // 动态添加asset目录
            AndroidSourceSet mainSourceSet = appExtension.getSourceSets().getByName("main");
            mainSourceSet.getAssets().srcDirs(flutterSOConfigFile.getParent());
            LogUtil.log("写入成功= " + flutterSOConfigFile.getAbsolutePath());
            LogUtil.log("配置内容= " + stringBuilder.toString());
            
            // 重置状态，为下次使用做准备
            initialized = false;
            stringBuilder = null;
        } catch (IOException e) {
            LogUtil.log("文件写入失败= " + e.getLocalizedMessage());
        }
    }
}
