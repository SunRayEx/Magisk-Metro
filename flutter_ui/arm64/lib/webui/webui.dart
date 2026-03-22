/// WebUI 模块
/// 
/// 提供 KernelSU 风格的 WebUI 功能
/// 支持 Magisk 模块的 WebUI 界面
/// 
/// 使用方法：
/// ```dart
/// import 'package:your_app/webui/webui.dart';
/// 
/// // 打开 WebUI 模块列表
/// Navigator.push(
///   context,
///   MaterialPageRoute(
///     builder: (context) => const WebUIModuleListScreen(),
///   ),
/// );
/// ```
library webui;

// 模块数据模型
export 'webui_module.dart';

// WebUI 类型检测
export 'webui_detector.dart';

// HTTP 服务器
export 'http_server_service.dart';

// KernelSU JavaScript Bridge
export 'kernelsu_js_bridge.dart';

// Root Shell 辅助工具
export 'root_shell_helper.dart';

// UI 页面
export 'webui_module_list.dart';
export 'webui_view_screen.dart';
