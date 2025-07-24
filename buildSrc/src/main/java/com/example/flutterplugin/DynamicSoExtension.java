package com.example.flutterplugin;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;

/**
 * 动态SO配置扩展
 */
public class DynamicSoExtension {
    private final NamedDomainObjectContainer<SoConfig> soConfigs;
    
    public DynamicSoExtension(Project project) {
        this.soConfigs = project.container(SoConfig.class);
    }
    
    public NamedDomainObjectContainer<SoConfig> getSoConfigs() {
        return soConfigs;
    }
    
    /**
     * 配置libapp
     */
    public void libapp(org.gradle.api.Action<? super SoConfig> action) {
        SoConfig config = soConfigs.maybeCreate("libapp");
        action.execute(config);
    }
    
    /**
     * 配置libflutter
     */
    public void libflutter(org.gradle.api.Action<? super SoConfig> action) {
        SoConfig config = soConfigs.maybeCreate("libflutter");
        action.execute(config);
    }
    
    /**
     * 检查是否配置了指定的SO
     */
    public boolean hasSoConfig(String soName) {
        return soConfigs.findByName(soName) != null;
    }
    
    /**
     * 获取指定SO的配置
     */
    public SoConfig getSoConfig(String soName) {
        return soConfigs.findByName(soName);
    }
    
}