package com.example.flutterplugin;

import com.android.build.gradle.AppExtension;
import com.example.flutterplugin.util.*;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class FlutterDynamicPlugin implements Plugin<Project> {
    
    private DynamicSoExtension dynamicSoExtension;
    
    @Override
    public void apply(Project project) {
        System.out.println("🚀 FlutterDynamicPlugin 开始应用到项目: " + project.getName());
        
        // 创建dynamicSo配置扩展
        dynamicSoExtension = project.getExtensions().create("dynamicSo", DynamicSoExtension.class, project);
        System.out.println("📝 已创建dynamicSo配置扩展");
        
        if (project.getPlugins().hasPlugin("com.android.application")) {
            System.out.println("✅ 检测到Android应用插件，开始配置动态SO插件");
            LogUtil.init(project);
            
            project.afterEvaluate(project1 -> {
                System.out.println("📋 项目评估完成，开始处理构建变体");
                printDynamicSoConfig();
                
                AppExtension appExtension = project.getExtensions().getByType(AppExtension.class);
                appExtension.getApplicationVariants().all(variant -> {
                    String variantName = StringUtil.capitalize(variant.getName());
                    System.out.println("🔧 处理构建变体: " + variantName);

                    Write2AssetsUtil.getInstance().init(appExtension, project.getBuildDir().getAbsolutePath());

                    // 统一创建和配置SO任务
                    java.util.List<SoDynamicTask> soDynamicTasks = new java.util.ArrayList<>();
                    Task mergeSOTask = project.getTasks().findByName("merge" + variantName + "NativeLibs");
                    
                    if (mergeSOTask == null) {
                        System.out.println("⚠️ 未找到merge" + variantName + "NativeLibs任务，跳过处理");
                        return;
                    }
                    
                    // 检查并创建所有配置的SO任务
                    String[] soTypes = {"libflutter", "libapp"};
                    for (String soType : soTypes) {
                        if (dynamicSoExtension.hasSoConfig(soType)) {
                            SoConfig soConfig = dynamicSoExtension.getSoConfig(soType);
                            if (ConfigValidator.validateSoConfig(soConfig)) {
                                String taskName = ("libflutter".equals(soType) ? "flutterSoDynamic" : "appSoDynamic") + variantName;
                                System.out.println("🔧 创建" + soType + "动态SO任务: " + taskName);
                                
                                SoDynamicTask soDynamicTask = project.getTasks().create(taskName, SoDynamicTask.class);
                                soDynamicTask.variant = variant;
                                soDynamicTask.appExtension = appExtension;
                                soDynamicTask.mergeNativeLibsOutputPath = mergeSOTask.getOutputs().getFiles().getAsPath();
                                soDynamicTask.soType = "libflutter".equals(soType) ? "engine" : "app";
                                soDynamicTask.soConfig = soConfig;
                                soDynamicTask.dynamicSoExtension = dynamicSoExtension;
                                
                                soDynamicTasks.add(soDynamicTask);
                                System.out.println("📋 " + soType + "配置: " + soConfig);
                            } else {
                                System.out.println("❌ " + soType + "配置验证失败，跳过处理");
                            }
                        }
                    }

                    // 如果没有配置任何SO，跳过
                    if (soDynamicTasks.isEmpty()) {
                        System.out.println("⚠️ 未配置任何动态SO，跳过处理");
                        return;
                    }

                    // 获取其他相关任务
                    Task stripTask = project.getTasks().findByName("strip" + variantName + "DebugSymbols");
                    Task packageTask = project.getTasks().findByName("package" + variantName);
                    
                    // 设置任务依赖关系
                    SoDynamicTask previousTask = null;
                    for (SoDynamicTask task : soDynamicTasks) {
                        if (previousTask == null) {
                            // 第一个任务直接依赖于mergeSOTask
                            mergeSOTask.finalizedBy(task);
                        } else {
                            // 后续任务依赖于前一个任务
                            previousTask.finalizedBy(task);
                            task.mustRunAfter(previousTask);
                        }
                        previousTask = task;
                    }
                    
                    // 确保在package任务之前完成
                    if (packageTask != null && !soDynamicTasks.isEmpty()) {
                        packageTask.mustRunAfter(soDynamicTasks.get(soDynamicTasks.size() - 1));
                    }
                    
                    // 如果有strip任务，确保在strip之前完成
                    if (stripTask != null && !soDynamicTasks.isEmpty()) {
                        stripTask.mustRunAfter(soDynamicTasks.get(soDynamicTasks.size() - 1));
                    }
                });
            });
        }
    }
    
    /**
     * 打印动态SO配置信息
     */
    private void printDynamicSoConfig() {
        System.out.println("📋 动态SO配置信息:");
        if (dynamicSoExtension.getSoConfigs().isEmpty()) {
            System.out.println("⚠️ 未配置任何动态SO");
        } else {
            dynamicSoExtension.getSoConfigs().forEach(config -> {
                System.out.println("  - " + config.getName() + ": " + config);
            });
        }
    }
}
