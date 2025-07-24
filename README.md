# Flutter 动态 SO 加载解决方案

## 📖 项目简介

这是一个完整的 Flutter 动态 SO 加载解决方案，实现了从构建到运行的全自动化流程：

- **构建时自动化**：从 APK 构建产物中自动提取真实的 Flutter SO 文件
- **智能上传**：自动上传 SO 包到本地服务器，支持版本管理和重复检测
- **运行时动态加载**：从服务器动态下载并加载 SO 文件
- **APK 瘦身**：移除 SO 文件后 APK 体积减少约 70%
- **多架构支持**：完整支持 arm64-v8a 和 armeabi-v7a 架构

## 🏗️ 项目架构

```
dynamicLoadFLutterSo/
├── README.md                           # 项目说明文档
├── app/                                # Android 主应用
│   ├── build.gradle                    # 应用构建配置
│   ├── src/main/assets/
│   │   ├── flutterso.json             # Flutter 引擎 SO 配置
│   │   └── appso.json                 # Flutter 应用 SO 配置
│   └── src/main/java/.../
│       ├── FlutterManager.kt          # SO 加载管理器
│       ├── SoPackageManager.kt        # SO 包管理器
│       ├── DynamicFlutterActivity.kt  # Flutter 界面
│       └── DynamicSoTestActivity.kt   # 测试工具界面
├── dart_server/                        # SO 包分发服务器
│   ├── server.sh                      # 服务器管理脚本
│   ├── bin/server.dart                # HTTP 服务器主程序
│   └── so_packages/                   # SO 包存储目录
├── flutter_module/                     # Flutter 模块
│   └── lib/main.dart                  # Flutter 应用代码
└── buildSrc/                          # Gradle 构建插件
    └── src/main/java/.../
        ├── FlutterDynamicPlugin.java  # 主插件类
        └── SoDynamicTask.java         # SO 处理任务
```

## 🚀 快速开始

### 1. 启动 SO 分发服务器

```bash
# 进入服务器目录并启动
cd dart_server
./server.sh
```

服务器启动后会：
- 自动杀掉占用 1234 端口的进程
- 设置 ADB 反向端口映射
- 启动 HTTP 服务器监听 1234 端口

### 2. 构建项目

```bash
# 构建 Release APK（自动处理 SO 文件）
./gradlew assembleRelease
```

构建过程会自动：
1. 从构建产物中提取 `libflutter.so` 和 `libapp.so`
2. 为每个架构创建独立的 ZIP 包
3. 上传 ZIP 包到本地服务器
4. 更新 assets 配置文件
5. 从 APK 中移除原始 SO 文件

### 3. 安装和测试

```bash
# 安装 APK
adb install -r app/build/outputs/apk/release/app-release.apk

# 启动应用
adb shell am start -n com.example.flutterdynamic/.MainActivity
```

## 📱 使用方法

### 方法一：应用界面操作
1. **打开应用** - 点击桌面图标启动
2. **点击"启动Flutter"** - 体验动态 SO 加载和 Flutter 界面
3. **点击"动态SO测试工具"** - 运行完整功能测试和日志查看

### 方法二：命令行测试
```bash
# 启动测试界面
adb shell am start -n com.example.flutterdynamic/.DynamicSoTestActivity

# 查看实时日志
adb logcat | grep -E "(FlutterManager|DynamicSo)"
```

## 🔧 核心技术特点

### 构建时自动化
- **真实 SO 提取**：从 APK 构建产物提取，非模拟文件
- **智能版本管理**：自动识别 Flutter SDK 版本和应用版本
- **重复检测**：避免重复上传相同版本的 SO 包
- **多架构处理**：为每个架构创建独立的 ZIP 包

### 运行时动态加载
- **网络通信**：通过 ADB 反向端口映射访问本地服务器
- **完整性校验**：MD5 和文件大小双重验证
- **架构适配**：自动选择匹配设备架构的 SO 文件
- **版本兼容性**：检查 SO 版本与应用版本的兼容性

### 服务器功能
- **HTTP API**：提供 RESTful 接口
- **文件管理**：自动管理 SO 包存储和版本
- **Web 界面**：浏览器访问查看 SO 包信息
- **上传支持**：支持构建时自动上传

## 📊 性能数据

| 指标 | 数值 | 说明 |
|------|------|------|
| APK 体积减少 | ~70% | 移除 SO 文件后的体积减少 |
| Flutter 引擎 SO | ~9MB | libflutter.so 压缩包大小 |
| Flutter 应用 SO | ~2.5MB | libapp.so 压缩包大小 |
| 首次下载时间 | 10-15秒 | 取决于网络环境 |
| 支持架构 | 2种 | arm64-v8a, armeabi-v7a |
| 自动化程度 | 100% | 无需手动脚本操作 |

## 🛠️ 配置说明

### Gradle 插件配置

在 `app/build.gradle` 中配置动态 SO：

```gradle
plugins {
    id 'com.example.flutterplugin'
}

dynamicSo {
    // 配置 libapp.so
    libapp {
        minVersion '1.0.0'
        maxVersion '6.8.8'
    }

    // 配置 libflutter.so
    libflutter {
        minVersion '1.0.0'
        maxVersion '9.8.8'
    }
}
```

### SO 配置文件

构建后自动生成的配置文件示例：

**flutterso.json**：
```json
{
  "armeabi-v7a": {
    "size": 7424684,
    "url": "http://127.0.0.1:1234/api/download/libflutter_1.0.0-xxx-armeabi-v7a.zip",
    "md5": "6a71f85cb8731717f2a069a2ee384a1e"
  },
  "arm64-v8a": {
    "size": 10554968,
    "url": "http://127.0.0.1:1234/api/download/libflutter_1.0.0-xxx-arm64-v8a.zip",
    "md5": "6507e161d6cf099baba4650abea05983"
  },
  "minAppVersion": "1.0.0",
  "libflutterVersion": "1.0.0",
  "maxAppVersion": "9.8.8"
}
```

## 🔄 工作流程详解

### 构建时流程
1. **Gradle 插件激活** → 检测到 Android 应用插件
2. **SO 文件发现** → 从 `merge{Variant}NativeLibs` 任务输出中查找
3. **版本识别** → 自动识别 Flutter SDK 版本和应用版本
4. **文件处理** → 为每个架构创建独立的 ZIP 包
5. **服务器上传** → 上传到本地 Dart 服务器
6. **配置更新** → 更新 assets 中的 JSON 配置文件
7. **文件清理** → 从 APK 中删除原始 SO 文件

### 运行时流程
1. **应用启动** → FlutterManager 初始化
2. **架构检测** → 获取设备支持的 ABI
3. **服务器请求** → 通过 127.0.0.1:1234 获取 SO 包列表
4. **版本匹配** → 选择兼容的 SO 包版本
5. **文件下载** → 下载对应架构的 ZIP 包
6. **完整性验证** → MD5 和文件大小校验
7. **解压加载** → 提取 SO 文件并动态加载
8. **Flutter 初始化** → 启动 Flutter 引擎

## 🔍 调试和日志

### 查看应用日志
```bash
# 查看 Flutter 管理器日志
adb logcat | grep FlutterManager

# 查看动态 SO 相关日志
adb logcat | grep -E "(DynamicSo|SoPackage)"
```

### 查看服务器日志
```bash
cd dart_server
cat server.log
```

### 测试网络连接
```bash
# 测试服务器状态
adb shell curl -s http://127.0.0.1:1234/api/status

# 查看 SO 包列表
adb shell curl -s http://127.0.0.1:1234/api/so-packages
```

### 构建日志分析
```bash
# 查看构建过程中的 SO 处理信息
./gradlew assembleRelease --info | grep -E "(flutterSo|appSo|SoDynamic)"
```

## 🌐 服务器 API

### 获取 SO 包列表
```
GET /api/so-packages
```

### 下载 SO 包
```
GET /api/download/<filename>
```

### 上传 SO 包
```
POST /api/upload
Content-Type: multipart/form-data
```

### 服务器状态
```
GET /api/status
```

### Web 界面
```
GET /
```

## 🛠️ 开发和扩展

### 添加新的架构支持
1. 在 `app/build.gradle` 中添加新的 ABI 过滤器
2. 更新 `FlutterManager.kt` 中的架构检测逻辑
3. 修改 `SoDynamicTask.java` 中的架构数组

### 自定义服务器地址
修改 `FlutterManager.kt` 中的服务器 URL：
```kotlin
private val localServerUrl = "http://your-server:port"
```

### 扩展配置选项
在 `DynamicSoExtension.java` 中添加新的配置项。

## 📝 注意事项

1. **服务器依赖**：构建前必须先启动本地服务器
2. **网络环境**：确保 ADB 反向端口映射正常工作
3. **架构兼容**：目前仅支持 ARM 架构（arm64-v8a, armeabi-v7a）
4. **版本管理**：SO 版本与应用版本需要保持兼容性
5. **存储空间**：首次运行需要下载 SO 文件，确保设备有足够空间

## 🔐 签名信息

- **Keystore 文件**：`app/flutter-dynamic.keystore`
- **Store 密码**：`123456`
- **Key 别名**：`flutter-dynamic`
- **Key 密码**：`123456`

## 🎯 完成状态

- ✅ **构建时自动化**：完全自动提取和上传 SO 文件
- ✅ **运行时动态加载**：支持多架构动态加载
- ✅ **网络通信**：ADB 反向端口映射通信
- ✅ **完整性校验**：MD5 和文件大小验证
- ✅ **版本管理**：自动版本识别和兼容性检查
- ✅ **APK 瘦身**：自动移除 SO 文件减少体积
- ✅ **服务器管理**：完整的 HTTP 服务器和 Web 界面
- ✅ **测试工具**：内置测试界面和日志查看

---

**这是一个生产级的 Flutter 动态 SO 加载解决方案，提供了从构建到运行的完整自动化流程，可直接应用于实际项目开发！** 🎉

## 📞 技术支持

如遇到问题，请检查：
1. 服务器是否正常启动（`./server.sh`）
2. ADB 连接是否正常（`adb devices`）
3. 端口映射是否生效（`adb reverse --list`）
4. 构建日志中的错误信息
5. 应用运行时的 logcat 输出