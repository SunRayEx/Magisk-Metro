import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'webui_module.dart';
import 'webui_view_screen.dart';
import 'webui_detector.dart';
import '../providers/dashboard_providers.dart';

/// WebUI 模块列表页面
/// 
/// 扫描 /data/adb/modules/ 目录
/// 显示所有包含 webroot/index.html 的模块
class WebUIModuleListScreen extends ConsumerStatefulWidget {
  const WebUIModuleListScreen({super.key});

  @override
  ConsumerState<WebUIModuleListScreen> createState() => _WebUIModuleListScreenState();
}

class _WebUIModuleListScreenState extends ConsumerState<WebUIModuleListScreen> {
  /// 模块列表
  List<WebUIModule> _modules = [];
  
  /// 是否正在加载
  bool _isLoading = true;
  
  /// 错误信息
  String? _error;
  
  @override
  void initState() {
    super.initState();
    _scanModules();
  }
  
  /// 扫描模块
  Future<void> _scanModules() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    
    try {
      final modules = await _scanWebUIModules();
      
      // 按名称排序
      modules.sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
      
      setState(() {
        _modules = modules;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = '扫描模块失败: $e';
        _isLoading = false;
      });
    }
  }
  
  /// 扫描 WebUI 模块
  Future<List<WebUIModule>> _scanWebUIModules() async {
    final modules = <WebUIModule>[];
    
    try {
      // 获取所有模块目录
      final result = await Process.run(
        'su',
        ['-c', 'ls -1 /data/adb/modules 2>/dev/null'],
      );
      
      if (result.exitCode != 0) {
        throw Exception('无法访问模块目录');
      }
      
      final lines = (result.stdout as String)
          .split('\n')
          .where((line) => line.trim().isNotEmpty)
          .toList();
      
      // 检查每个模块是否有 WebUI
      for (final moduleId in lines) {
        final module = await _checkModule(moduleId.trim());
        if (module != null) {
          modules.add(module);
        }
      }
    } catch (e) {
      debugPrint('[WebUI] Scan error: $e');
      rethrow;
    }
    
    return modules;
  }
  
  /// 检查模块是否有 WebUI
  Future<WebUIModule?> _checkModule(String moduleId) async {
    final modulePath = '/data/adb/modules/$moduleId';
    
    try {
      // 检查 webroot/index.html 是否存在
      final hasWebUI = await WebUIDetector.hasWebUI(modulePath);
      if (!hasWebUI) {
        return null;
      }
      
      // 读取 module.prop
      final propResult = await Process.run(
        'su',
        ['-c', 'cat "$modulePath/module.prop" 2>/dev/null'],
      );
      
      String name = moduleId;
      String version = 'unknown';
      String author = 'unknown';
      String description = '';
      
      if (propResult.exitCode == 0) {
        final propContent = propResult.stdout as String;
        final props = _parseModuleProp(propContent);
        
        name = props['name'] ?? moduleId;
        version = props['version'] ?? 'unknown';
        author = props['author'] ?? 'unknown';
        description = props['description'] ?? '';
      }
      
      // 检测 WebUI 类型
      final detection = await WebUIDetector.detect(modulePath);
      
      return WebUIModule(
        id: moduleId,
        name: name,
        version: version,
        author: author,
        description: description,
        path: modulePath,
        webuiType: detection.type,
        redirectPort: detection.redirectPort,
      );
    } catch (e) {
      debugPrint('[WebUI] Check module $moduleId error: $e');
      return null;
    }
  }
  
  /// 解析 module.prop
  Map<String, String> _parseModuleProp(String content) {
    final props = <String, String>{};
    
    for (final line in content.split('\n')) {
      final trimmed = line.trim();
      if (trimmed.isEmpty || trimmed.startsWith('#')) continue;
      
      final eqIndex = trimmed.indexOf('=');
      if (eqIndex > 0) {
        final key = trimmed.substring(0, eqIndex).trim();
        final value = trimmed.substring(eqIndex + 1).trim();
        props[key] = value;
      }
    }
    
    return props;
  }
  
  /// 打开 WebUI
  void _openWebUI(WebUIModule module) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => WebUIViewScreen(module: module),
      ),
    );
  }
  
  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    
    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      appBar: AppBar(
        backgroundColor: AppTheme.getTile(isDark),
        elevation: 0,
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: Icon(Icons.chevron_left, color: AppTheme.getFont(isDark), size: 28),
        ),
        title: Text(
          'WebUI 模块',
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w600,
            color: AppTheme.getFont(isDark),
          ),
        ),
        actions: [
          IconButton(
            onPressed: _scanModules,
            icon: Icon(Icons.refresh, color: AppTheme.getFont(isDark)),
          ),
        ],
      ),
      body: _buildBody(isDark, tileColorIndex),
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
            CircularProgressIndicator(color: widgetColor),
            const SizedBox(height: 16),
            Text(
              '扫描模块...',
              style: GoogleFonts.poppins(color: AppTheme.getFont(isDark)),
            ),
          ],
        ),
      );
    }
    
    // 错误
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 64, color: Colors.red.withOpacity(0.7)),
              const SizedBox(height: 16),
              Text(
                _error!,
                style: GoogleFonts.poppins(color: AppTheme.getFont(isDark)),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _scanModules,
                style: ElevatedButton.styleFrom(backgroundColor: widgetColor),
                child: const Text('重试', style: TextStyle(color: Colors.white)),
              ),
            ],
          ),
        ),
      );
    }
    
    // 空列表
    if (_modules.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.web_asset_off, size: 64, color: AppTheme.getFont(isDark).withOpacity(0.5)),
            const SizedBox(height: 16),
            Text(
              '没有找到支持 WebUI 的模块',
              style: GoogleFonts.poppins(color: AppTheme.getFont(isDark).withOpacity(0.7)),
            ),
            const SizedBox(height: 8),
            Text(
              '模块需要包含 webroot/index.html',
              style: GoogleFonts.poppins(
                color: AppTheme.getFont(isDark).withOpacity(0.5),
                fontSize: 12,
              ),
            ),
          ],
        ),
      );
    }
    
    // 模块列表
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: _modules.length,
      itemBuilder: (context, index) {
        final module = _modules[index];
        return RepaintBoundary( // ← 这里改了: 隔离高频重绘模块列表项
          child: _buildModuleCard(module, isDark, tileColorIndex, widgetColor),
        );
      },
    );
  }
  
  Widget _buildModuleCard(WebUIModule module, bool isDark, int tileColorIndex, Color widgetColor) {
    final isRedirect = module.webuiType == WebUIType.redirect;
    
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppTheme.getTile(isDark),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(isDark ? 0.3 : 0.08),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => _openWebUI(module),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                // 图标
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: widgetColor.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    isRedirect ? Icons.link : Icons.web,
                    color: widgetColor,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 16),
                // 信息
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              module.name,
                              style: GoogleFonts.poppins(
                                fontWeight: FontWeight.w600,
                                fontSize: 14,
                                color: AppTheme.getFont(isDark),
                              ),
                            ),
                          ),
                          // 类型标签
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: (isRedirect ? Colors.orange : Colors.green).withOpacity(0.2),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              isRedirect ? 'REDIRECT' : 'STANDARD',
                              style: GoogleFonts.jetBrainsMono(
                                fontSize: 8,
                                color: isRedirect ? Colors.orange : Colors.green,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'v${module.version} • ${module.author}',
                        style: GoogleFonts.poppins(
                          fontSize: 11,
                          color: AppTheme.getFont(isDark).withOpacity(0.6),
                        ),
                      ),
                      if (module.description.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          module.description,
                          style: GoogleFonts.poppins(
                            fontSize: 10,
                            color: AppTheme.getFont(isDark).withOpacity(0.5),
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                      if (isRedirect && module.redirectPort != null) ...[
                        const SizedBox(height: 4),
                        Text(
                          '端口: ${module.redirectPort}',
                          style: GoogleFonts.jetBrainsMono(
                            fontSize: 9,
                            color: Colors.orange.withOpacity(0.8),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                // 箭头
                Icon(
                  Icons.chevron_right,
                  color: AppTheme.getFont(isDark).withOpacity(0.5),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
