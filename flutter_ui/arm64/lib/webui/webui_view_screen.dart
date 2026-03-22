import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'webui_module.dart';
import 'webui_detector.dart';
import 'http_server_service.dart';
import 'kernelsu_js_bridge.dart';
import '../providers/dashboard_providers.dart';

/// WebUI WebView 页面
///
/// 使用 webview_flutter + 内置 HttpServer 方案
/// 彻底解决 ERR_CACHE_MISS 问题
///
/// 支持两种 WebUI 类型：
/// - 标准型：启动本地 HTTP 服务器，注入 KernelSU JavaScript API
/// - 重定向型：直接加载 index.html 中指定的重定向 URL（模块自己的 HTTP 服务）
class WebUIViewScreen extends ConsumerStatefulWidget {
  final WebUIModule module;

  const WebUIViewScreen({
    super.key,
    required this.module,
  });

  @override
  ConsumerState<WebUIViewScreen> createState() => _WebUIViewScreenState();
}

class _WebUIViewScreenState extends ConsumerState<WebUIViewScreen> {
  /// WebViewController
  late final WebViewController _controller;

  /// HTTP 服务器（仅标准型使用）
  WebUIHttpServer? _server;

  /// JS Bridge（仅标准型使用）
  KernelSUJsBridge? _jsBridge;

  /// 检测结果
  WebUIDetectionResult? _detectionResult;

  /// 是否正在加载
  bool _isLoading = true;

  /// 是否有错误
  bool _hasError = false;

  /// 错误信息
  String _errorMessage = '';

  /// 是否全屏模式
  bool _isFullScreen = false;

  /// 是否使用 file:// 回退方案
  bool _useFallback = false;

  /// 加载进度
  int _loadingProgress = 0;

  String get _moduleDir => widget.module.path;
  String get _moduleId => widget.module.id;

  @override
  void initState() {
    super.initState();
    _initWebView();
  }

  @override
  void dispose() {
    _cleanup();
    SystemChrome.setEnabledSystemUIMode(
      SystemUiMode.edgeToEdge,
      overlays: SystemUiOverlay.values,
    );
    super.dispose();
  }

  /// 初始化 WebView
  Future<void> _initWebView() async {
    // 创建 WebViewController
    _controller = WebViewController();

    // 配置 WebView
    await _controller.setJavaScriptMode(JavaScriptMode.unrestricted);
    await _controller.setNavigationDelegate(
      NavigationDelegate(
        onProgress: (int progress) {
          setState(() {
            _loadingProgress = progress;
          });
        },
        onPageStarted: (String url) {
          debugPrint('[WebUI] Page started: $url');
        },
        onPageFinished: (String url) async {
          debugPrint('[WebUI] Page finished: $url');
          // 注入 JS API（仅标准型）
          if (_detectionResult?.type == WebUIType.standard) {
            await _jsBridge?.injectJavaScriptAPI();
          }
        },
        onWebResourceError: (WebResourceError error) async {
          debugPrint('[WebUI] Resource error: ${error.description}');
          // 处理 ERR_CACHE_MISS
          if (error.errorCode == -1 ||
              error.description.contains('ERR_CACHE_MISS') ||
              error.description.contains('net::ERR')) {
            await _handleLoadError(error.description);
          }
        },
        onNavigationRequest: (NavigationRequest request) {
          debugPrint('[WebUI] Navigation request: ${request.url}');

          try {
            final uri = Uri.parse(request.url);
            final scheme = uri.scheme.toLowerCase();
            final host = uri.host.toLowerCase();

            // 允许 file:// 协议（回退方案）
            if (scheme == 'file') {
              debugPrint('[WebUI] Allowing file:// URL');
              return NavigationDecision.navigate;
            }

            // 允许 about:blank 和 data: 协议
            if (scheme == 'about' || scheme == 'data') {
              debugPrint('[WebUI] Allowing about/data URL');
              return NavigationDecision.navigate;
            }

            // 允许 http/https 协议的 localhost 地址
            if (scheme == 'http' || scheme == 'https') {
              // 检查是否是 localhost 地址
              if (host == 'localhost' ||
                  host == '127.0.0.1' ||
                  host == '::1' ||
                  host == '[::1]' ||
                  host.isEmpty) {
                debugPrint('[WebUI] Allowing localhost HTTP URL: $host');
                return NavigationDecision.navigate;
              }

              // 额外检查：URL 字符串中是否包含 localhost/127.0.0.1
              // 这是为了处理某些特殊格式的 URL
              final urlStr = request.url.toLowerCase();
              if (urlStr.contains('127.0.0.1') ||
                  urlStr.contains('localhost')) {
                debugPrint('[WebUI] Allowing URL containing localhost: $urlStr');
                return NavigationDecision.navigate;
              }
            }

            // 阻止其他外部 URL
            debugPrint('[WebUI] Blocking external URL: ${request.url} (scheme=$scheme, host=$host)');
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('已阻止外部链接: ${request.url}'),
                  duration: const Duration(seconds: 2),
                ),
              );
            }
            return NavigationDecision.prevent;
          } catch (e) {
            debugPrint('[WebUI] Error parsing URL: $e');
            // URL 解析失败时允许加载
            return NavigationDecision.navigate;
          }
        },
      ),
    );

    // 启用缩放
    await _controller.enableZoom(true);

    // 清除缓存
    await _controller.clearCache();
    await _controller.clearLocalStorage();

    // 检测模块类型
    await _detectAndSetup();
  }

  /// 检测模块类型并设置服务器
  Future<void> _detectAndSetup() async {
    try {
      debugPrint('[WebUI] Detecting module type for $_moduleDir');

      // 检测 WebUI 类型
      _detectionResult = await WebUIDetector.detect(_moduleDir);

      debugPrint('[WebUI] Detection result: type=${_detectionResult!.type}, '
          'redirectUrl=${_detectionResult!.redirectUrl}, '
          'redirectPort=${_detectionResult!.redirectPort}');

      if (_detectionResult!.type == WebUIType.unknown) {
        setState(() {
          _hasError = true;
          _errorMessage = '无法识别 WebUI 类型';
          _isLoading = false;
        });
        return;
      }

      // 根据类型设置
      if (_detectionResult!.type == WebUIType.redirect) {
        // 重定向型：直接加载模块自己的 HTTP 服务
        await _setupRedirectType();
      } else {
        // 标准型：启动本地 HTTP 服务器并注入 JS API
        await _setupStandardType();
      }
    } catch (e, stack) {
      debugPrint('[WebUI] Setup error: $e\n$stack');
      setState(() {
        _hasError = true;
        _errorMessage = '设置 WebUI 失败: $e';
        _isLoading = false;
      });
    }
  }

  /// 设置重定向型模块
  ///
  /// 直接加载 index.html 中指定的重定向 URL
  Future<void> _setupRedirectType() async {
    final redirectUrl = _detectionResult!.redirectUrl;

    if (redirectUrl == null || redirectUrl.isEmpty) {
      setState(() {
        _hasError = true;
        _errorMessage = '重定向 URL 为空';
        _isLoading = false;
      });
      return;
    }

    debugPrint('[WebUI] Setting up redirect type: $redirectUrl');

    // 显示提示
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
              '检测到重定向型 WebUI，正在连接 ${_detectionResult!.redirectPort ?? "未知端口"}...'),
          duration: const Duration(seconds: 2),
        ),
      );
    }

    // 直接加载重定向 URL
    try {
      await _controller.loadRequest(Uri.parse(redirectUrl));

      setState(() {
        _isLoading = false;
      });

      debugPrint('[WebUI] Loaded redirect URL: $redirectUrl');
    } catch (e) {
      debugPrint('[WebUI] Failed to load redirect URL: $e');
      await _handleLoadError(e.toString());
    }
  }

  /// 设置标准型模块
  ///
  /// 启动本地 HTTP 服务器并注入 KernelSU JS API
  Future<void> _setupStandardType() async {
    debugPrint('[WebUI] Setting up standard type');

    // 创建 JS Bridge
    _jsBridge = KernelSUJsBridge(
      moduleDir: _moduleDir,
      moduleId: _moduleId,
      onToast: _showToast,
      onFullScreen: _handleFullScreen,
    );
    _jsBridge!.setController(_controller);

    // 启动 HTTP 服务器
    final serverManager = WebUIHttpServerManager();
    _server = await serverManager.getOrCreateServer(
      moduleId: _moduleId,
      modulePath: _moduleDir,
      webuiType: WebUIType.standard,
    );

    if (_server == null) {
      setState(() {
        _hasError = true;
        _errorMessage = '启动 HTTP 服务器失败';
        _isLoading = false;
      });
      return;
    }

    debugPrint('[WebUI] Server started at ${_server!.baseUrl}');

    // 加载页面
    await _loadWebUI();
  }

  /// 加载 WebUI
  Future<void> _loadWebUI() async {
    if (_server == null) return;

    final url = _server!.indexUrl;
    debugPrint('[WebUI] Loading: $url');

    try {
      await _controller.loadRequest(Uri.parse(url));

      setState(() {
        _isLoading = false;
        _useFallback = false;
      });
    } catch (e) {
      debugPrint('[WebUI] Load error: $e');
      await _handleLoadError(e.toString());
    }
  }

  /// 处理加载错误
  Future<void> _handleLoadError(String error) async {
    debugPrint('[WebUI] Handling load error: $error');

    // 如果还没有使用回退方案，尝试 file://
    if (!_useFallback) {
      debugPrint('[WebUI] Attempting file:// fallback');

      _useFallback = true;

      // 显示提示
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('HTTP 加载失败，切换到 file:// 方案'),
            duration: Duration(seconds: 2),
          ),
        );
      }

      // 尝试使用 file:// 加载
      final filePath = 'file://$_moduleDir/webroot/index.html';
      debugPrint('[WebUI] Trying file://: $filePath');

      try {
        await _controller.loadRequest(Uri.parse(filePath));
        setState(() {
          _isLoading = false;
        });
        return;
      } catch (e) {
        debugPrint('[WebUI] file:// fallback also failed: $e');
      }
    }

    // 所有方案都失败
    setState(() {
      _hasError = true;
      _errorMessage = '加载 WebUI 失败:\n$error';
      _isLoading = false;
    });
  }

  /// 清理资源
  Future<void> _cleanup() async {
    await WebUIHttpServerManager().stopServer(_moduleId);
  }

  /// 显示 Toast
  void _showToast(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  /// 处理全屏切换
  void _handleFullScreen(bool enable) {
    setState(() {
      _isFullScreen = enable;
    });

    if (enable) {
      SystemChrome.setEnabledSystemUIMode(
        SystemUiMode.immersiveSticky,
        overlays: [],
      );
    } else {
      SystemChrome.setEnabledSystemUIMode(
        SystemUiMode.edgeToEdge,
        overlays: SystemUiOverlay.values,
      );
    }
  }

  /// 刷新页面
  Future<void> _refresh() async {
    setState(() {
      _isLoading = true;
      _hasError = false;
      _useFallback = false;
      _loadingProgress = 0;
    });

    await _controller.clearCache();
    await _controller.clearLocalStorage();

    // 根据类型重新加载
    if (_detectionResult?.type == WebUIType.redirect &&
        _detectionResult!.redirectUrl != null) {
      try {
        await _controller.loadRequest(Uri.parse(_detectionResult!.redirectUrl!));
        setState(() {
          _isLoading = false;
        });
      } catch (e) {
        await _handleLoadError(e.toString());
      }
    } else if (_server != null) {
      await _loadWebUI();
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);

    return PopScope(
      canPop: !_isFullScreen,
      onPopInvokedWithResult: (didPop, result) {
        if (_isFullScreen) {
          _handleFullScreen(false);
        }
      },
      child: Scaffold(
        backgroundColor: isDark ? const Color(0xFF1a1a1a) : Colors.white,
        appBar: _isFullScreen ? null : _buildAppBar(isDark, tileColorIndex),
        body: _buildBody(isDark, tileColorIndex),
      ),
    );
  }

  PreferredSizeWidget _buildAppBar(bool isDark, int tileColorIndex) {
    final widgetColor = AppTheme.getTileWidgetColor(3, tileColorIndex, isDark);

    return AppBar(
      backgroundColor: AppTheme.getTile(isDark),
      elevation: 0,
      leading: GestureDetector(
        onTap: () => Navigator.pop(context),
        child:
            Icon(Icons.chevron_left, color: AppTheme.getFont(isDark), size: 28),
      ),
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            widget.module.name,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w600,
              fontSize: 16,
              color: AppTheme.getFont(isDark),
            ),
          ),
          Text(
            'v${widget.module.version}',
            style: GoogleFonts.poppins(
              fontSize: 10,
              color: AppTheme.getFont(isDark).withOpacity(0.6),
            ),
          ),
        ],
      ),
      actions: [
        // WebUI 类型指示器
        Container(
          margin: const EdgeInsets.symmetric(horizontal: 4),
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: widgetColor.withOpacity(0.2),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            _detectionResult?.type == WebUIType.redirect
                ? 'REDIRECT'
                : 'STANDARD',
            style: GoogleFonts.jetBrainsMono(
              fontSize: 9,
              color: widgetColor,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        // 端口/URL 指示器
        if (_detectionResult?.type == WebUIType.redirect &&
            _detectionResult!.redirectPort != null)
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 4),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.orange.withOpacity(0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              ':${_detectionResult!.redirectPort}',
              style: GoogleFonts.jetBrainsMono(
                fontSize: 10,
                color: Colors.orange,
                fontWeight: FontWeight.w600,
              ),
            ),
          )
        else if (_server != null)
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 4),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.blue.withOpacity(0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              ':${_server!.port}',
              style: GoogleFonts.jetBrainsMono(
                fontSize: 10,
                color: Colors.blue,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        IconButton(
          onPressed: _refresh,
          icon: Icon(Icons.refresh, color: AppTheme.getFont(isDark)),
        ),
      ],
    );
  }

  Widget _buildBody(bool isDark, int tileColorIndex) {
    final widgetColor = AppTheme.getTileWidgetColor(3, tileColorIndex, isDark);

    // 加载中
    if (_isLoading) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(
              value: _loadingProgress / 100.0,
              color: widgetColor,
            ),
            const SizedBox(height: 16),
            Text(
              '加载 WebUI... $_loadingProgress%',
              style: GoogleFonts.poppins(color: AppTheme.getFont(isDark)),
            ),
            if (_detectionResult != null) ...[
              const SizedBox(height: 8),
              Text(
                '类型: ${_detectionResult!.type.name}${_detectionResult!.type == WebUIType.redirect ? " → :${_detectionResult!.redirectPort}" : ""}',
                style: GoogleFonts.poppins(
                  color: AppTheme.getFont(isDark).withOpacity(0.6),
                  fontSize: 12,
                ),
              ),
            ],
          ],
        ),
      );
    }

    // 错误状态
    if (_hasError) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline,
                  size: 64, color: Colors.red.withOpacity(0.7)),
              const SizedBox(height: 16),
              Text(
                _errorMessage,
                style: GoogleFonts.poppins(color: AppTheme.getFont(isDark)),
                textAlign: TextAlign.center,
              ),
              if (_useFallback) ...[
                const SizedBox(height: 8),
                Text(
                  '(使用 file:// 回退方案)',
                  style: GoogleFonts.poppins(
                    color: AppTheme.getFont(isDark).withOpacity(0.6),
                    fontSize: 12,
                  ),
                ),
              ],
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _refresh,
                style: ElevatedButton.styleFrom(backgroundColor: widgetColor),
                child: const Text('重试',
                    style: TextStyle(color: Colors.white)),
              ),
            ],
          ),
        ),
      );
    }

    // WebView - 使用 WebViewWidget
    // 注意：JavaScript Channels 已通过 WebViewController.addJavaScriptChannel() 添加
    return Stack(
      children: [
        WebViewWidget(
          controller: _controller,
        ),
        // 加载进度条
        if (_loadingProgress < 100)
          LinearProgressIndicator(
            value: _loadingProgress / 100.0,
            backgroundColor: Colors.transparent,
            valueColor: AlwaysStoppedAnimation<Color>(widgetColor),
          ),
      ],
    );
  }
}
