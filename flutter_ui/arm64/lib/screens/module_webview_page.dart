import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter/services.dart';
import '../providers/dashboard_providers.dart';
import '../models/models.dart';
import '../l10n/app_localizations.dart';

/// Module WebUI Page using Android PlatformView
/// 
/// This uses the native WebView with WebViewAssetLoader to avoid
/// ERR_CACHE_MISS errors that occur with Flutter's webview_flutter
/// when loading local files.
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
  static const MethodChannel _channel = MethodChannel('magisk_manager/webui');
  
  bool _isLoading = true;
  bool _hasError = false;
  String _errorMessage = '';
  String? _loaderUrl;
  
  String get _moduleDir => widget.module.path;
  String get _moduleId => _moduleDir.split('/').last;
  
  @override
  void initState() {
    super.initState();
    _setupWebUI();
  }
  
  Future<void> _setupWebUI() async {
    try {
      debugPrint('[WebUI] Setting up WebUI for ${widget.module.name}');
      debugPrint('[WebUI] Module path: $_moduleDir');
      debugPrint('[WebUI] webUIUrl parameter: ${widget.webUIUrl}');
      
      // Setup WebViewAssetLoader on native side
      // This handles both local webroot and external URLs
      final result = await _channel.invokeMethod<bool>('setupWebViewAssetLoader', {
        'moduleDir': _moduleDir,
      });
      
      if (result != true) {
        setState(() {
          _hasError = true;
          _errorMessage = 'Module has no WebUI (webroot not found)';
          _isLoading = false;
        });
        return;
      }
      
      // Get the URL to load
      final loaderUrl = await _channel.invokeMethod<String>('getWebUILoaderUrl');
      if (loaderUrl == null) {
        setState(() {
          _hasError = true;
          _errorMessage = 'Failed to get WebUI URL';
          _isLoading = false;
        });
        return;
      }
      
      debugPrint('[WebUI] Loader URL: $loaderUrl');
      _loaderUrl = loaderUrl;
      
      setState(() {
        _isLoading = false;
      });
    } catch (e) {
      debugPrint('[WebUI] Setup error: $e');
      setState(() {
        _hasError = true;
        _errorMessage = 'Failed to setup WebUI: $e';
        _isLoading = false;
      });
    }
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
          IconButton(
            onPressed: () {
              setState(() {
                _isLoading = true;
                _hasError = false;
              });
              _setupWebUI();
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
              Icon(Icons.error_outline, size: 64, color: Colors.red.withOpacity(0.7)),
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
    
    // Use AndroidView to embed native WebView with WebViewAssetLoader
    return AndroidView(
      viewType: 'magiskube-webui',
      creationParams: {
        'modulePath': _moduleDir,
        'moduleId': _moduleId,
      },
      creationParamsCodec: const StandardMessageCodec(),
      onPlatformViewCreated: (id) {
        debugPrint('[WebUI] PlatformView created with id: $id');
      },
    );
  }
}
