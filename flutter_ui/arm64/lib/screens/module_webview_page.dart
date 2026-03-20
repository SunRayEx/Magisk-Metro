import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:path_provider/path_provider.dart';
import '../providers/dashboard_providers.dart';
import '../models/models.dart';
import '../services/android_data_service.dart';
import '../l10n/app_localizations.dart';

/// Module WebUI Page - Fixed for ERR_CACHE_MISS issue
/// 
/// Solution: Copy webroot files to app's cache directory first,
/// then serve from there using a local HTTP server.
/// This approach is similar to how KsuWebUI works.
class ModuleWebUIPage extends ConsumerStatefulWidget {
  final Module module;
  final String? webUIUrl;

  const ModuleWebUIPage({
    super.key,
    required this.module,
    this.webUIUrl,
  });

  @override
  ConsumerState<ModuleWebUIPage> createState() => _ModuleWebUIPageState();
}

class _ModuleWebUIPageState extends ConsumerState<ModuleWebUIPage> {
  WebViewController? _controller;
  bool _isLoading = true;
  bool _hasError = false;
  String _errorMessage = '';
  String? _localServerUrl;
  HttpServer? _httpServer;
  String? _cacheDir;
  
  String get _moduleDir => widget.module.path;
  
  /// Generate a unique ID for the module from its path
  String get _moduleId => _moduleDir.split('/').last;
  
  @override
  void initState() {
    super.initState();
    _setupWebUI();
  }
  
  /// Setup WebUI - copy files and start local server
  Future<void> _setupWebUI() async {
    try {
      debugPrint('[WebUI] Setting up WebUI for ${widget.module.name}');
      
      // If external URL is provided, use it directly
      if (widget.webUIUrl != null && widget.webUIUrl!.isNotEmpty) {
        debugPrint('[WebUI] Using external URL: ${widget.webUIUrl}');
        setState(() {
          _isLoading = false;
        });
        _initWebView();
        return;
      }
      
      // Step 1: Get app's cache directory
      final cacheDir = await getTemporaryDirectory();
      final webuiCacheDir = Directory('${cacheDir.path}/webui/$_moduleId');
      _cacheDir = webuiCacheDir.path;
      
      // Clean up old cache
      if (await webuiCacheDir.exists()) {
        await webuiCacheDir.delete(recursive: true);
      }
      await webuiCacheDir.create(recursive: true);
      debugPrint('[WebUI] Cache directory: ${webuiCacheDir.path}');
      
      // Step 2: Copy webroot files from module to cache
      final webrootSource = '$_moduleDir/webroot';
      final copyResult = await _copyWebrootToCache(webrootSource, webuiCacheDir.path);
      
      if (!copyResult) {
        setState(() {
          _hasError = true;
          _errorMessage = 'Module has no WebUI (webroot not found)';
          _isLoading = false;
        });
        return;
      }
      
      // Step 3: Start local HTTP server
      final server = await _startLocalServer(webuiCacheDir.path);
      if (server == null) {
        setState(() {
          _hasError = true;
          _errorMessage = 'Failed to start local server';
          _isLoading = false;
        });
        return;
      }
      
      _httpServer = server;
      _localServerUrl = 'http://localhost:${server.port}/index.html';
      
      debugPrint('[WebUI] Local server started: $_localServerUrl');
      
      setState(() {
        _isLoading = false;
      });
      
      _initWebView();
    } catch (e) {
      debugPrint('[WebUI] Setup error: $e');
      setState(() {
        _hasError = true;
        _errorMessage = 'Failed to setup WebUI: $e';
        _isLoading = false;
      });
    }
  }
  
  /// Copy webroot files from module to app's cache directory using root
  Future<bool> _copyWebrootToCache(String sourcePath, String destPath) async {
    try {
      // First check if webroot exists using fileExistsAsRoot
      final exists = await AndroidDataService.fileExistsAsRoot(sourcePath);
      if (!exists) {
        debugPrint('[WebUI] Webroot not found: $sourcePath');
        return false;
      }
      
      // Read index.html first to verify webroot is valid
      final indexContent = await AndroidDataService.readFileAsRoot('$sourcePath/index.html');
      if (indexContent == null || indexContent.isEmpty) {
        debugPrint('[WebUI] index.html not found or empty');
        return false;
      }
      
      // Write index.html to cache
      final indexFile = File('$destPath/index.html');
      await indexFile.writeAsString(indexContent);
      
      // Try to copy common web assets (CSS, JS, etc.)
      // We'll read common files and copy them if they exist
      final commonAssets = [
        'style.css',
        'styles.css',
        'main.css',
        'app.css',
        'script.js',
        'main.js',
        'app.js',
        'index.js',
      ];
      
      for (final asset in commonAssets) {
        final content = await AndroidDataService.readFileAsRoot('$sourcePath/$asset');
        if (content != null && content.isNotEmpty) {
          final file = File('$destPath/$asset');
          await file.writeAsString(content);
          debugPrint('[WebUI] Copied: $asset');
        }
      }
      
      debugPrint('[WebUI] Webroot copied successfully');
      return true;
    } catch (e) {
      debugPrint('[WebUI] Copy error: $e');
      return false;
    }
  }
  
  /// Start a local HTTP server to serve webroot files
  Future<HttpServer?> _startLocalServer(String webrootPath) async {
    try {
      // Bind to localhost on a random available port
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      
      debugPrint('[WebUI] HTTP server listening on port ${server.port}');
      
      server.listen((HttpRequest request) async {
        try {
          // Handle requests
          final path = request.uri.path;
          String filePath = webrootPath + path;
          
          // Default to index.html
          if (path == '/' || path.isEmpty) {
            filePath = '$webrootPath/index.html';
          }
          
          final file = File(filePath);
          
          if (await file.exists()) {
            // Determine content type
            String contentType = 'text/html';
            if (path.endsWith('.css')) {
              contentType = 'text/css';
            } else if (path.endsWith('.js')) {
              contentType = 'application/javascript';
            } else if (path.endsWith('.json')) {
              contentType = 'application/json';
            } else if (path.endsWith('.png')) {
              contentType = 'image/png';
            } else if (path.endsWith('.jpg') || path.endsWith('.jpeg')) {
              contentType = 'image/jpeg';
            } else if (path.endsWith('.svg')) {
              contentType = 'image/svg+xml';
            } else if (path.endsWith('.woff') || path.endsWith('.woff2')) {
              contentType = 'font/woff2';
            }
            
            request.response.headers.contentType = ContentType.parse(contentType);
            request.response.headers.set('Access-Control-Allow-Origin', '*');
            request.response.headers.set('Cache-Control', 'no-cache');
            
            await file.openRead().pipe(request.response);
          } else {
            request.response.statusCode = HttpStatus.notFound;
            request.response.write('Not Found: $path');
            await request.response.close();
          }
        } catch (e) {
          debugPrint('[WebUI] Request error: $e');
          try {
            request.response.statusCode = HttpStatus.internalServerError;
            await request.response.close();
          } catch (_) {}
        }
      });
      
      return server;
    } catch (e) {
      debugPrint('[WebUI] Server start error: $e');
      return null;
    }
  }
  
  void _initWebView() async {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (url) {
            debugPrint('[WebUI] Loading: $url');
          },
          onPageFinished: (url) {
            debugPrint('[WebUI] Finished: $url');
          },
          onWebResourceError: (error) {
            debugPrint('[WebUI] Error: ${error.description} (${error.errorCode}) for ${error.url}');
          },
        ),
      );
    
    if (_localServerUrl != null) {
      debugPrint('[WebUI] Loading URL: $_localServerUrl');
      await _controller!.loadRequest(Uri.parse(_localServerUrl!));
    } else if (widget.webUIUrl != null) {
      debugPrint('[WebUI] Loading external URL: ${widget.webUIUrl}');
      await _controller!.loadRequest(Uri.parse(widget.webUIUrl!));
    }
    
    setState(() {});
  }
  
  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(3, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;
    
    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF1a1a1a) : Colors.white,
      appBar: AppBar(
        backgroundColor: AppTheme.getTile(isDark),
        elevation: 0,
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: Icon(Icons.chevron_left, color: AppTheme.getFont(isDark), size: 28),
        ),
        title: Text(
          widget.module.name,
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w900,
            fontSize: 18,
            color: AppTheme.getFont(isDark),
          ),
        ),
        actions: [
          if (_controller != null)
            IconButton(
              onPressed: () {
                _controller?.reload();
              },
              icon: Icon(Icons.refresh, color: widgetColor),
            ),
        ],
      ),
      body: _buildBody(isDark, widgetColor, localizations),
    );
  }
  
  Widget _buildBody(bool isDark, Color widgetColor, AppLocalizations localizations) {
    if (_isLoading) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(color: widgetColor),
            const SizedBox(height: 16),
            Text(
              'Loading WebUI...',
              style: GoogleFonts.poppins(color: AppTheme.getFont(isDark)),
            ),
          ],
        ),
      );
    }
    
    if (_hasError) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 64, color: Colors.red.withValues(alpha: 0.7)),
              const SizedBox(height: 16),
              Text(
                _errorMessage,
                style: GoogleFonts.poppins(color: AppTheme.getFont(isDark)),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: () {
                  setState(() {
                    _hasError = false;
                    _isLoading = true;
                    _localServerUrl = null;
                  });
                  _setupWebUI();
                },
                style: ElevatedButton.styleFrom(backgroundColor: widgetColor),
                child: const Text('Retry', style: TextStyle(color: Colors.white)),
              ),
            ],
          ),
        ),
      );
    }
    
    if (_controller == null) {
      return Center(
        child: CircularProgressIndicator(color: widgetColor),
      );
    }
    
    return WebViewWidget(controller: _controller!);
  }
  
  @override
  void dispose() {
    _controller = null;
    _httpServer?.close();
    _httpServer = null;
    super.dispose();
  }
}
