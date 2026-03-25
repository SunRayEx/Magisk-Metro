import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter/services.dart';
import '../providers/dashboard_providers.dart';
import '../models/models.dart';

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
  String get _moduleDir => widget.module.path;
  String get _moduleId => _moduleDir.split('/').last;
  
  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);
    
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
      ),
      body: AndroidView(
        viewType: 'magiskube-webui',
        creationParams: {
          'modulePath': _moduleDir,
          'moduleId': _moduleId,
        },
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: (id) {
          debugPrint('[WebUI] PlatformView created with id: $id');
        },
      ),
    );
  }
}
