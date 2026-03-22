import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'webui_detector.dart';

/// WebUI 本地 HTTP 服务器
/// 
/// 使用纯 dart:io HttpServer 将模块的 webroot 目录映射到 http://127.0.0.1:端口/
/// 彻底解决 WebView 加载本地文件时的 net::ERR_CACHE_MISS 问题
/// 
/// 支持两种 WebUI 类型：
/// - 标准型：使用随机端口，正常映射 webroot
/// - 重定向型：强制使用模块期望的端口（如 9090），并处理 /ui 等路径
class WebUIHttpServer {
  /// 服务器实例
  HttpServer? _server;
  
  /// 模块的 webroot 路径
  final String webrootPath;
  
  /// 模块完整路径
  final String modulePath;
  
  /// 模块 ID
  final String moduleId;
  
  /// WebUI 类型
  final WebUIType webuiType;
  
  /// 重定向端口（仅 redirect 类型使用）
  final int? redirectPort;
  
  /// 服务器端口
  int _port = 0;
  
  /// 是否使用 Root Shell 读取文件
  final bool useRootShell;
  
  /// 文件缓存（小文件）
  final Map<String, Uint8List> _fileCache = {};
  
  /// 最大缓存文件大小（1MB）
  static const int _maxCacheSize = 1024 * 1024;
  
  /// 是否正在运行
  bool get isRunning => _server != null;
  
  /// 获取服务器端口
  int get port => _port;
  
  /// 获取服务器基础 URL
  String get baseUrl => 'http://127.0.0.1:$_port';
  
  /// 获取 index.html URL
  String get indexUrl => '$baseUrl/index.html';
  
  WebUIHttpServer({
    required this.webrootPath,
    required this.modulePath,
    required this.moduleId,
    required this.webuiType,
    this.redirectPort,
    this.useRootShell = true,
  });
  
  /// 启动服务器
  /// 
  /// 对于重定向型模块，使用模块期望的端口（如 9090）
  /// 对于标准型模块，使用随机可用端口
  Future<bool> start() async {
    if (_server != null) {
      debugPrint('[WebUIHttpServer] 服务器已在运行');
      return true;
    }
    
    // 确定要使用的端口
    int targetPort;
    if (webuiType == WebUIType.redirect && redirectPort != null) {
      // 重定向型：强制使用模块期望的端口
      targetPort = redirectPort!;
      debugPrint('[WebUIHttpServer] 重定向型模块，尝试使用端口: $targetPort');
    } else {
      // 标准型：尝试使用 8080，如果被占用则递增
      targetPort = 8080;
    }
    
    // 尝试绑定端口
    const int maxRetries = 50;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      final tryPort = targetPort + attempt;
      try {
        _server = await HttpServer.bind('127.0.0.1', tryPort);
        _port = tryPort;
        break;
      } catch (e) {
        debugPrint('[WebUIHttpServer] 端口 $tryPort 绑定失败: $e');
        if (attempt == maxRetries - 1) {
          debugPrint('[WebUIHttpServer] 所有端口尝试失败');
          return false;
        }
        // 对于重定向型，端口必须精确匹配，不能使用其他端口
        if (webuiType == WebUIType.redirect && redirectPort != null) {
          debugPrint('[WebUIHttpServer] 重定向型模块端口 ${redirectPort!} 被占用，无法启动');
          return false;
        }
      }
    }
    
    if (_server == null) {
      return false;
    }
    
    debugPrint('[WebUIHttpServer] 服务器启动成功，端口: $_port，类型: $webuiType');
    
    // 开始监听请求
    _server!.listen(_handleRequest, onError: (error) {
      debugPrint('[WebUIHttpServer] 服务器错误: $error');
    });
    
    return true;
  }
  
  /// 停止服务器
  Future<void> stop() async {
    if (_server != null) {
      await _server!.close();
      _server = null;
      _fileCache.clear();
      debugPrint('[WebUIHttpServer] 服务器已停止');
    }
  }
  
  /// 处理 HTTP 请求
  Future<void> _handleRequest(HttpRequest request) async {
    final path = request.uri.path;
    
    debugPrint('[WebUIHttpServer] 请求: $path');
    
    try {
      // 重定向型模块特殊处理
      if (webuiType == WebUIType.redirect) {
        await _handleRedirectRequest(request, path);
        return;
      }
      
      // 标准型模块处理
      await _handleStandardRequest(request, path);
    } catch (e, stack) {
      debugPrint('[WebUIHttpServer] 处理请求错误: $e\n$stack');
      try {
        _sendError(request, 500, 'Internal Server Error: $e');
      } catch (_) {}
    }
  }
  
  /// 处理重定向型模块的请求
  Future<void> _handleRedirectRequest(HttpRequest request, String path) async {
    // 重定向型模块的 index.html 会跳转到 http://127.0.0.1:9090/ui
    // 我们需要：
    // 1. 提供 /index.html 让浏览器执行跳转
    // 2. 处理 /ui 路径，尝试提供真实的 UI 文件
    
    if (path == '/' || path == '/index.html') {
      // 返回原始 index.html（包含跳转逻辑）
      final fileData = await _readFile('$webrootPath/index.html');
      if (fileData != null) {
        _sendFile(request, fileData, 'text/html; charset=utf-8');
        return;
      }
      _sendError(request, 404, 'index.html not found');
      return;
    }
    
    // 处理 /ui 路径 - 尝试查找模块的真实 UI 文件
    if (path == '/ui' || path.startsWith('/ui/')) {
      // 尝试多种可能的 UI 文件位置
      final uiPaths = [
        '$webrootPath/ui/index.html',
        '$webrootPath/ui.html',
        '$webrootPath/web/index.html',
        '$modulePath/webroot/ui/index.html',
        '$modulePath/ui/index.html',
      ];
      
      for (final uiPath in uiPaths) {
        final fileData = await _readFile(uiPath);
        if (fileData != null) {
          _sendFile(request, fileData, 'text/html; charset=utf-8');
          return;
        }
      }
      
      // 如果找不到 UI 文件，返回提示
      _sendError(request, 404, 'UI file not found.\n\n'
          'This is a redirect-type module.\n'
          'The module expects to redirect to: http://127.0.0.1:$port/ui\n\n'
          'Please ensure the module\'s own HTTP service is running.');
      return;
    }
    
    // 其他路径，尝试从 webroot 读取
    await _handleStandardRequest(request, path);
  }
  
  /// 处理标准型模块的请求
  Future<void> _handleStandardRequest(HttpRequest request, String path) async {
    // 安全检查：防止路径遍历攻击
    final normalizedPath = _normalizePath(path);
    if (normalizedPath == null) {
      _sendError(request, 403, 'Forbidden');
      return;
    }
    
    // 构建文件路径
    String filePath = '$webrootPath$normalizedPath';
    
    // 如果是目录，尝试加载 index.html
    if (normalizedPath.endsWith('/')) {
      filePath += 'index.html';
    }
    
    // 检查文件是否存在并读取
    final fileData = await _readFile(filePath);
    
    if (fileData == null) {
      _sendError(request, 404, 'Not Found: $path');
      return;
    }
    
    // 获取 MIME 类型
    final mimeType = _getMimeType(filePath);
    
    _sendFile(request, fileData, mimeType);
  }
  
  /// 发送文件响应
  void _sendFile(HttpRequest request, Uint8List data, String mimeType) {
    request.response.headers.contentType = ContentType.parse(mimeType);
    request.response.headers.set('Cache-Control', 'no-cache, no-store, must-revalidate');
    request.response.headers.set('Pragma', 'no-cache');
    request.response.headers.set('Expires', '0');
    request.response.headers.set('Access-Control-Allow-Origin', '*');
    
    // 处理 Range 请求
    final rangeHeader = request.headers.value('range');
    if (rangeHeader != null) {
      _handleRangeRequest(request, data, rangeHeader);
    } else {
      request.response.contentLength = data.length;
      request.response.add(data);
    }
    
    request.response.close();
  }
  
  /// 规范化路径，防止路径遍历攻击
  String? _normalizePath(String path) {
    // 解码 URL 编码
    final decoded = Uri.decodeComponent(path);
    
    // 移除开头的斜杠
    var normalized = decoded.startsWith('/') ? decoded.substring(1) : decoded;
    
    // 检查路径遍历
    final parts = normalized.split('/');
    final stack = <String>[];
    
    for (final part in parts) {
      if (part == '..') {
        if (stack.isEmpty) return null; // 试图跳出根目录
        stack.removeLast();
      } else if (part != '.' && part.isNotEmpty) {
        stack.add(part);
      }
    }
    
    final result = stack.join('/');
    return result.isEmpty ? '/' : '/$result';
  }
  
  /// 读取文件内容
  Future<Uint8List?> _readFile(String filePath) async {
    try {
      // 首先尝试直接读取
      final file = File(filePath);
      if (await file.exists()) {
        return await file.readAsBytes();
      }
      
      // 如果使用 Root Shell，尝试通过 su 读取
      if (useRootShell) {
        return await _readFileWithRoot(filePath);
      }
      
      return null;
    } catch (e) {
      debugPrint('[WebUIHttpServer] 读取文件失败: $filePath, 错误: $e');
      
      // 尝试 Root Shell
      if (useRootShell) {
        return await _readFileWithRoot(filePath);
      }
      
      return null;
    }
  }
  
  /// 使用 Root Shell 读取文件
  Future<Uint8List?> _readFileWithRoot(String filePath) async {
    try {
      // 使用 base64 编码来安全传输二进制数据
      final result = await Process.run(
        'su',
        ['-c', 'base64 "$filePath" 2>/dev/null'],
      );
      
      if (result.exitCode == 0 && result.stdout.toString().isNotEmpty) {
        final base64Str = (result.stdout as String).trim();
        if (base64Str.isNotEmpty) {
          return base64Decode(base64Str);
        }
      }
      
      return null;
    } catch (e) {
      debugPrint('[WebUIHttpServer] Root Shell 读取失败: $filePath, 错误: $e');
      return null;
    }
  }
  
  /// 处理 Range 请求
  void _handleRangeRequest(HttpRequest request, Uint8List data, String rangeHeader) {
    // 解析 Range 头
    final match = RegExp(r'bytes=(\d+)-(\d*)').firstMatch(rangeHeader);
    if (match == null) {
      request.response.statusCode = 400;
      return;
    }
    
    final start = int.parse(match.group(1)!);
    final end = match.group(2)!.isNotEmpty 
        ? int.parse(match.group(2)!) 
        : data.length - 1;
    
    if (start >= data.length || end >= data.length || start > end) {
      request.response.statusCode = 416; // Range Not Satisfiable
      request.response.headers.set('Content-Range', 'bytes */${data.length}');
      return;
    }
    
    final chunkLength = end - start + 1;
    
    request.response.statusCode = 206; // Partial Content
    request.response.headers.set('Content-Range', 'bytes $start-$end/${data.length}');
    request.response.contentLength = chunkLength;
    request.response.add(data.sublist(start, end + 1));
  }
  
  /// 发送错误响应
  void _sendError(HttpRequest request, int statusCode, String message) {
    request.response.statusCode = statusCode;
    request.response.headers.contentType = ContentType.text;
    request.response.write(message);
    request.response.close();
  }
  
  /// 获取文件的 MIME 类型
  static String _getMimeType(String filePath) {
    final extension = filePath.split('.').last.toLowerCase();
    
    const mimeTypes = <String, String>{
      // 文本类型
      'html': 'text/html; charset=utf-8',
      'htm': 'text/html; charset=utf-8',
      'css': 'text/css; charset=utf-8',
      'js': 'application/javascript; charset=utf-8',
      'mjs': 'application/javascript; charset=utf-8',
      'json': 'application/json; charset=utf-8',
      'xml': 'application/xml; charset=utf-8',
      'txt': 'text/plain; charset=utf-8',
      'md': 'text/markdown; charset=utf-8',
      
      // 图片类型
      'png': 'image/png',
      'jpg': 'image/jpeg',
      'jpeg': 'image/jpeg',
      'gif': 'image/gif',
      'svg': 'image/svg+xml',
      'ico': 'image/x-icon',
      'webp': 'image/webp',
      'bmp': 'image/bmp',
      
      // 字体类型
      'woff': 'font/woff',
      'woff2': 'font/woff2',
      'ttf': 'font/ttf',
      'otf': 'font/otf',
      'eot': 'application/vnd.ms-fontobject',
      
      // 音视频类型
      'mp3': 'audio/mpeg',
      'wav': 'audio/wav',
      'ogg': 'audio/ogg',
      'mp4': 'video/mp4',
      'webm': 'video/webm',
      
      // 其他类型
      'pdf': 'application/pdf',
      'zip': 'application/zip',
      'wasm': 'application/wasm',
    };
    
    return mimeTypes[extension] ?? 'application/octet-stream';
  }
}

/// WebUI HttpServer 管理器
/// 
/// 单例模式，管理所有模块的 HTTP 服务器
class WebUIHttpServerManager {
  static final WebUIHttpServerManager _instance = WebUIHttpServerManager._internal();
  factory WebUIHttpServerManager() => _instance;
  WebUIHttpServerManager._internal();
  
  /// 模块 ID 到服务器的映射
  final Map<String, WebUIHttpServer> _servers = {};
  
  /// 获取或创建模块的服务器
  /// 
  /// [moduleId] 模块 ID
  /// [modulePath] 模块完整路径
  /// [webuiType] WebUI 类型
  /// [redirectPort] 重定向端口（仅重定向型需要）
  Future<WebUIHttpServer?> getOrCreateServer({
    required String moduleId,
    required String modulePath,
    required WebUIType webuiType,
    int? redirectPort,
  }) async {
    // 如果已存在，直接返回
    if (_servers.containsKey(moduleId)) {
      final server = _servers[moduleId]!;
      if (server.isRunning) {
        return server;
      }
      // 服务器已停止，移除并重新创建
      _servers.remove(moduleId);
    }
    
    // 创建新服务器
    final server = WebUIHttpServer(
      webrootPath: '$modulePath/webroot',
      modulePath: modulePath,
      moduleId: moduleId,
      webuiType: webuiType,
      redirectPort: redirectPort,
      useRootShell: true,
    );
    
    if (await server.start()) {
      _servers[moduleId] = server;
      return server;
    }
    
    return null;
  }
  
  /// 停止指定模块的服务器
  Future<void> stopServer(String moduleId) async {
    final server = _servers.remove(moduleId);
    if (server != null) {
      await server.stop();
    }
  }
  
  /// 停止所有服务器
  Future<void> stopAll() async {
    for (final server in _servers.values) {
      await server.stop();
    }
    _servers.clear();
  }
  
  /// 获取所有运行中的服务器
  List<WebUIHttpServer> get runningServers => 
      _servers.values.where((s) => s.isRunning).toList();
}
