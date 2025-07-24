package com.example.flutterplugin;

import com.android.build.gradle.AppExtension;
import com.example.flutterplugin.util.*;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class FlutterDynamicPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        System.out.println("🚀 FlutterDynamicPlugin 开始应用到项目: " + project.getName());
        
        if (project.getPlugins().hasPlugin("com.android.application")) {
            System.out.println("✅ 检测到Android应用插件，开始配置动态SO插件");
            LogUtil.init(project);
            
            project.afterEvaluate(project1 -> {
                System.out.println("📋 项目评估完成，开始处理构建变体");
                AppExtension appExtension = project.getExtensions().getByType(AppExtension.class);
                appExtension.getApplicationVariants().all(variant -> {
                    String variantName = StringUtil.capitalize(variant.getName());
                    System.out.println("🔧 处理构建变体: " + variantName);
                    
                    // 移除release限制，支持所有变体
                    // if (!variantName.equalsIgnoreCase("release")) return;

                    Write2AssetsUtil.getInstance().init(appExtension, project.getBuildDir().getAbsolutePath());

                    //处理 libflutter.so
                    EngineSoDynamicTask engineSoDynamicTask = project.getTasks().create("flutterSoDynamic" + variantName, EngineSoDynamicTask.class);
                    //处理 libapp.so
                    AppSoDynamicTask appSoDynamicTask = project.getTasks().create("appSoDynamic" + variantName, AppSoDynamicTask.class);

                    // 在strip任务之后执行删除操作
                    Task mergeSOTask = project.getTasks().findByName("merge" + variantName + "NativeLibs");
                    Task stripTask = project.getTasks().findByName("strip" + variantName + "DebugSymbols");
                    Task packageTask = project.getTasks().findByName("package" + variantName);
                    
                    // 让我们的任务在package之后执行，直接修改APK
                    if (packageTask != null) {
                        packageTask.finalizedBy(engineSoDynamicTask);
                        engineSoDynamicTask.finalizedBy(appSoDynamicTask);
                    } else if (stripTask != null) {
                        stripTask.finalizedBy(engineSoDynamicTask);
                        engineSoDynamicTask.finalizedBy(appSoDynamicTask);
                    } else {
                        mergeSOTask.finalizedBy(engineSoDynamicTask);
                        engineSoDynamicTask.finalizedBy(appSoDynamicTask);
                    }
                    
                    appSoDynamicTask.mustRunAfter(engineSoDynamicTask);
                    
                    // 移除assets依赖，避免循环依赖
                    // Task mergeAssetsTask = project.getTasks().findByName("merge" + variantName + "Assets");
                    // mergeAssetsTask.dependsOn(appSoDynamicTask);

                    engineSoDynamicTask.variant = variant;
                    engineSoDynamicTask.appExtension = appExtension;
                    engineSoDynamicTask.mergeNativeLibsOutputPath = mergeSOTask.getOutputs().getFiles().getAsPath();

                    appSoDynamicTask.variant = variant;
                    appSoDynamicTask.appExtension = appExtension;
                    appSoDynamicTask.mergeNativeLibsOutputPath = mergeSOTask.getOutputs().getFiles().getAsPath();
                });
            });
        }
    }
}
