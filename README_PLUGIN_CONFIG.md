# Flutter 动态 SO 插件配置指南

## 概述

Flutter 动态 SO 插件现在支持通过 `build.gradle` 文件进行灵活配置，只有在 `dynamicSo` 配置块中声明的 SO 文件才会被处理（上传并从 APK 中删除）。

## 配置语法

在你的 `app/build.gradle` 文件中添加 `dynamicSo` 配置块：

```gradle
dynamicSo {
    libapp {
        minVersion '1.0.0'
        maxVersion '8.8.8'
        uploadUrl 'https://your-server.com/upload'
        downloadUrl 'https://your-server.com/download'
    }
    
    libflutter {
        minVersion '1.0.0'
        maxVersion '8.8.8'
        uploadUrl 'https://your-server.com/upload'
        downloadUrl 'https://your-server.com/download'
    }
}
```

## 配置参数说明

### SO 配置块

每个 SO 文件（如 `libapp`、`libflutter`）都可以单独配置以下参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `minVersion` | String | 否 | "1.0.0" | 支持的最小应用版本 |
| `maxVersion` | String | 否 | "9.9.9" | 支持的最大应用版本 |
| `uploadUrl` | String | 否 | "" | 自定义上传服务器地址 |
| `downloadUrl` | String | 否 | "" | 自定义下载服务器地址 |

### 版本号格式

版本号必须遵循 `x.y.z` 格式，例如：
- ✅ `1.0.0`
- ✅ `2.1.5`
- ✅ `10.20.30`
- ❌ `1.0`
- ❌ `v1.0.0`
- ❌ `1.0.0-beta`

## 使用示例

### 基础配置

只处理 `libapp.so`：

```gradle
dynamicSo {
    libapp {
        minVersion '1.0.0'
        maxVersion '5.0.0'
    }
}
```

### 完整配置

处理两个 SO 文件并设置所有参数：

```gradle
dynamicSo {
    libapp {
        minVersion '1.0.0'
        maxVersion '8.8.8'
        uploadUrl 'https://app-server.com/upload'
        downloadUrl 'https://app-server.com/download'
    }
    
    libflutter {
        minVersion '2.0.0'
        maxVersion '6.0.0'
        uploadUrl 'https://flutter-server.com/upload'
        downloadUrl 'https://flutter-server.com/download'
    }
}
```

### 选择性处理

如果只想处理某个 SO 文件，只需配置对应的块即可。例如，只处理 `libapp.so`：

```gradle
dynamicSo {
    libapp {
        minVersion '1.0.0'
        maxVersion '8.8.8'
    }
    // 不配置 libflutter，则不会处理 libflutter.so
}
```

## 工作流程

1. **配置验证**：插件会验证配置的有效性，包括版本号格式和版本范围
2. **任务创建**：只为配置了的 SO 文件创建对应的处理任务
3. **SO 处理**：
   - 检查服务器是否已存在该版本的 SO
   - 如果不存在，创建 ZIP 包并上传
   - 上传成功后从 APK 中删除原始 SO 文件
4. **配置文件生成**：在 `assets` 目录下生成对应的 JSON 配置文件

## 生成的配置文件

插件会在 `src/main/assets/` 目录下生成配置文件：

- `appso.json`：libapp.so 的配置信息
- `flutterso.json`：libflutter.so 的配置信息

配置文件示例：
```json
{
  "libappVersion": "1.0.0",
  "minAppVersion": "1.0.0",
  "maxAppVersion": "8.8.8",
  "uploadUrl": "https://your-server.com/upload",
  "downloadUrl": "https://your-server.com/download",
  "arm64-v8a": {
    "url": "https://server.com/libapp_1.0.0-abc123-arm64-v8a.zip",
    "md5": "abc123...",
    "size": 1234567
  },
  "armeabi-v7a": {
    "url": "https://server.com/libapp_1.0.0-def456-armeabi-v7a.zip",
    "md5": "def456...",
    "size": 987654
  }
}
```

## 注意事项

1. **只处理配置的 SO**：未在 `dynamicSo` 块中配置的 SO 文件不会被处理
2. **配置验证**：无效的配置会导致对应的 SO 处理被跳过
3. **版本兼容性**：确保 `minVersion` 不大于 `maxVersion`
4. **网络依赖**：需要确保上传服务器可访问
5. **构建顺序**：插件会自动处理任务依赖关系，确保在正确的时机执行

## 故障排除

### 常见错误

1. **配置验证失败**
   ```
   ❌ 最小版本号格式无效: 1.0
   ```
   解决：使用正确的版本号格式，如 `1.0.0`

2. **版本范围错误**
   ```
   ❌ 最小版本不能大于最大版本: 2.0.0 > 1.0.0
   ```
   解决：确保 `minVersion` ≤ `maxVersion`

3. **未找到 SO 文件**
   ```
   未找到任何架构的libapp.so文件
   ```
   解决：检查 Flutter 模块是否正确集成

### 调试信息

插件会输出详细的日志信息，包括：
- 配置解析结果
- SO 文件查找过程
- 上传进度
- 文件删除结果

查看构建日志以获取详细的执行信息。