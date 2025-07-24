import 'dart:io';
import 'dart:convert';
import 'dart:typed_data';
import 'package:shelf/shelf.dart';
import 'package:shelf/shelf_io.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:shelf_cors_headers/shelf_cors_headers.dart';
import 'package:path/path.dart' as path;
import 'package:crypto/crypto.dart';

/// Flutter SO包分发服务器
/// 用于向Android应用提供动态SO包下载服务
class FlutterSoServer {
  static const int defaultPort = 1234;
  static const String soPackagesDir = 'so_packages';
  
  late final Router _router;
  late final Directory _packagesDir;
  
  FlutterSoServer() {
    _setupRouter();
    _setupPackagesDirectory();
  }
  
  void _setupRouter() {
    _router = Router()
      ..get('/', _handleRoot)
      ..get('/api/so-packages', _handleGetSoPackages)
      ..get('/api/download/<filename>', _handleDownloadSoPackage)
      ..post('/api/upload', _handleUploadSoPackage)
      ..get('/api/status', _handleStatus);
  }
  
  void _setupPackagesDirectory() {
    _packagesDir = Directory(soPackagesDir);
    if (!_packagesDir.existsSync()) {
      _packagesDir.createSync(recursive: true);
      print('📁 创建SO包目录: ${_packagesDir.absolute.path}');
    }
  }
  
  /// 根路径处理
  Response _handleRoot(Request request) {
    final html = '''
<!DOCTYPE html>
<html>
<head>
    <title>Flutter SO包分发服务器</title>
    <meta charset="utf-8">
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .header { color: #2196F3; }
        .package { border: 1px solid #ddd; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .status { color: #4CAF50; font-weight: bold; }
    </style>
</head>
<body>
    <h1 class="header">🚀 Flutter SO包分发服务器</h1>
    <p class="status">✅ 服务器运行正常</p>
    
    <h2>📋 API接口</h2>
    <ul>
        <li><code>GET /api/so-packages</code> - 获取SO包列表</li>
        <li><code>GET /api/download/&lt;filename&gt;</code> - 下载SO包</li>
        <li><code>POST /api/upload</code> - 上传SO包</li>
        <li><code>GET /api/status</code> - 服务器状态</li>
    </ul>
    
    <h2>📦 当前SO包</h2>
    <div id="packages">加载中...</div>
    
    <script>
        fetch('/api/so-packages')
            .then(response => response.json())
            .then(packages => {
                const container = document.getElementById('packages');
                if (packages.length === 0) {
                    container.innerHTML = '<p>暂无SO包</p>';
                } else {
                    container.innerHTML = packages.map(pkg => 
                        `<div class="package">
                            <strong>\${pkg.fileName}</strong><br>
                            版本: \${pkg.version}<br>
                            大小: \${(pkg.size / 1024 / 1024).toFixed(2)} MB<br>
                            MD5: \${pkg.md5}<br>
                            <a href="/api/download/\${pkg.fileName}">下载</a>
                        </div>`
                    ).join('');
                }
            })
            .catch(err => {
                document.getElementById('packages').innerHTML = '<p>加载失败: ' + err + '</p>';
            });
    </script>
</body>
</html>
    ''';
    
    return Response.ok(html, headers: {'Content-Type': 'text/html; charset=utf-8'});
  }
  
  /// 获取SO包列表
  Response _handleGetSoPackages(Request request) {
    try {
      final packages = <Map<String, dynamic>>[];
      
      for (final file in _packagesDir.listSync()) {
        if (file is File && file.path.endsWith('.zip')) {
          final packageInfo = _extractPackageInfo(file);
          if (packageInfo != null) {
            packages.add(packageInfo);
          }
        }
      }
      
      // 按版本排序
      packages.sort((a, b) => _compareVersions(b['version'], a['version']));
      
      // 按版本排序
      packages.sort((a, b) => _compareVersions(b['version'], a['version']));
      
      print('📋 返回SO包列表，共 ${packages.length} 个包');
      return Response.ok(
        jsonEncode(packages),
        headers: {'Content-Type': 'application/json; charset=utf-8'}
      );
    } catch (e) {
      print('❌ 获取SO包列表失败: $e');
      return Response.internalServerError(body: '获取SO包列表失败: $e');
    }
  }
  
  /// 下载SO包
  Response _handleDownloadSoPackage(Request request) {
    final filename = request.params['filename']!;
    final file = File(path.join(_packagesDir.path, filename));
    
    if (!file.existsSync()) {
      print('❌ 文件不存在: $filename');
      return Response.notFound('文件不存在: $filename');
    }
    
    try {
      final bytes = file.readAsBytesSync();
      print('📥 下载SO包: $filename (${bytes.length} bytes)');
      
      return Response.ok(
        bytes,
        headers: {
          'Content-Type': 'application/zip',
          'Content-Disposition': 'attachment; filename="$filename"',
          'Content-Length': '${bytes.length}',
        }
      );
    } catch (e) {
      print('❌ 下载文件失败: $e');
      return Response.internalServerError(body: '下载文件失败: $e');
    }
  }
  
  /// 上传SO包
  Future<Response> _handleUploadSoPackage(Request request) async {
    try {
      final contentType = request.headers['content-type'];
      if (contentType == null || !contentType.contains('multipart/form-data')) {
        return Response.badRequest(body: '请使用multipart/form-data格式上传');
      }
      
      // 读取请求体
      final bytes = await request.read().expand((chunk) => chunk).toList();
      final data = Uint8List.fromList(bytes);
      
      // 解析multipart数据，提取文件名
      String filename = 'uploaded_${DateTime.now().millisecondsSinceEpoch}.zip';
      
      // 简单的multipart解析，查找filename
      final dataString = String.fromCharCodes(data);
      final filenameMatch = RegExp(r'filename="([^"]+)"').firstMatch(dataString);
      if (filenameMatch != null) {
        filename = filenameMatch.group(1) ?? filename;
      }
      
      // 查找文件数据的开始位置（在两个连续的\r\n\r\n之后）
      final headerEndIndex = dataString.indexOf('\r\n\r\n');
      if (headerEndIndex == -1) {
        return Response.badRequest(body: '无效的multipart数据格式');
      }
      
      // 提取实际的文件数据
      final fileDataStart = headerEndIndex + 4;
      final fileData = data.sublist(fileDataStart);
      
      // 移除末尾的boundary数据
      var actualFileData = fileData;
      final boundaryIndex = _findBoundaryEnd(fileData);
      if (boundaryIndex > 0) {
        actualFileData = fileData.sublist(0, boundaryIndex);
      }
      
      final file = File(path.join(_packagesDir.path, filename));
      await file.writeAsBytes(actualFileData);
      
      print('📤 构建时上传SO包成功: $filename (${actualFileData.length} bytes)');
      print('📁 保存位置: ${file.absolute.path}');
      
      return Response.ok(
        jsonEncode({
          'success': true,
          'filename': filename,
          'size': actualFileData.length,
          'url': '/api/download/$filename',
        }),
        headers: {'Content-Type': 'application/json; charset=utf-8'}
      );
    } catch (e) {
      print('❌ 上传文件失败: $e');
      return Response.internalServerError(
        body: jsonEncode({
          'success': false,
          'error': '上传文件失败: $e'
        }),
        headers: {'Content-Type': 'application/json; charset=utf-8'}
      );
    }
  }
  
  /// 查找multipart boundary结束位置
  int _findBoundaryEnd(Uint8List data) {
    // 查找 \r\n-- 模式，这通常是boundary的开始
    for (int i = 0; i < data.length - 4; i++) {
      if (data[i] == 0x0D && data[i + 1] == 0x0A && 
          data[i + 2] == 0x2D && data[i + 3] == 0x2D) {
        return i;
      }
    }
    return -1;
  }
  
  /// 服务器状态
  Response _handleStatus(Request request) {
    final packageCount = _packagesDir.listSync()
        .where((file) => file is File && file.path.endsWith('.zip'))
        .length;
    
    final status = {
      'status': 'running',
      'timestamp': DateTime.now().toIso8601String(),
      'packageCount': packageCount,
      'packagesDir': _packagesDir.absolute.path,
    };
    
    return Response.ok(
      jsonEncode(status),
      headers: {'Content-Type': 'application/json; charset=utf-8'}
    );
  }
  
  /// 提取包信息
  Map<String, dynamic>? _extractPackageInfo(File zipFile) {
    try {
      final stat = zipFile.statSync();
      final bytes = zipFile.readAsBytesSync();
      final md5Hash = md5.convert(bytes).toString();
      
      // 从文件名提取版本信息
      final filename = path.basename(zipFile.path);
      final version = _extractVersionFromFilename(filename) ?? '1.0.0';
      
      final packageInfo = <String, dynamic>{};
      packageInfo['fileName'] = filename;
      packageInfo['version'] = version;
      packageInfo['size'] = stat.size;
      packageInfo['md5'] = md5Hash;
      packageInfo['url'] = '/api/download/$filename';
      packageInfo['abi'] = 'arm64-v8a';
      return packageInfo;
    } catch (e) {
      print('❌ 提取包信息失败: ${zipFile.path}, $e');
      return null;
    }
  }
  
  /// 从文件名提取版本
  String? _extractVersionFromFilename(String filename) {
    final regex = RegExp(r'_(\d+\.\d+\.\d+)\.zip$');
    final match = regex.firstMatch(filename);
    return match?.group(1);
  }
  
  /// 版本比较
  int _compareVersions(String version1, String version2) {
    final v1Parts = version1.split('.').map(int.parse).toList();
    final v2Parts = version2.split('.').map(int.parse).toList();
    
    for (int i = 0; i < 3; i++) {
      final v1 = i < v1Parts.length ? v1Parts[i] : 0;
      final v2 = i < v2Parts.length ? v2Parts[i] : 0;
      
      if (v1 != v2) return v1.compareTo(v2);
    }
    
    return 0;
  }
  
  /// 启动服务器
  Future<void> start({int port = defaultPort}) async {
    final handler = Pipeline()
        .addMiddleware(corsHeaders())
        .addMiddleware(logRequests())
        .addHandler(_router);
    
    // 监听所有网络接口，允许外部设备访问
    final server = await serve(handler, InternetAddress.anyIPv4, port);
    
    print('🚀 Flutter SO包分发服务器启动成功!');
    print('📍 本地地址: http://127.0.0.1:${server.port}');
    print('📍 网络地址: http://192.168.1.2:${server.port}');
    print('📁 SO包目录: ${_packagesDir.absolute.path}');
    print('💡 Android设备可直接访问网络地址');
    print('');
    print('按 Ctrl+C 停止服务器');
  }
}

void main(List<String> arguments) async {
  int port = FlutterSoServer.defaultPort;
  
  // 解析命令行参数
  if (arguments.isNotEmpty) {
    try {
      port = int.parse(arguments[0]);
    } catch (e) {
      print('❌ 无效的端口号: ${arguments[0]}');
      exit(1);
    }
  }
  
  final server = FlutterSoServer();
  await server.start(port: port);
}