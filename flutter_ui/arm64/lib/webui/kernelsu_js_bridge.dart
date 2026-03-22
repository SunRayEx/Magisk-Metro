import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// KernelSU JavaScript Bridge
///
/// 为 WebView 提供 KernelSU 兼容的 JavaScript API
/// 完全符合 kernelsu.org 规范
///
/// 支持的方法：
/// - exec(command: String) → {errno, stdout, stderr}
/// - execRoot(command: String) → {errno, stdout, stderr}
/// - getModuleDir() → String
/// - toast(message: String)
/// - goFullScreen()
class KernelSUJsBridge {
  /// 模块目录路径
  final String moduleDir;

  /// 模块 ID
  final String moduleId;

  /// Toast 回调
  final void Function(String message)? onToast;

  /// 全屏切换回调
  final void Function(bool enable)? onFullScreen;

  /// WebViewController 引用
  WebViewController? _controller;

  KernelSUJsBridge({
    required this.moduleDir,
    required this.moduleId,
    this.onToast,
    this.onFullScreen,
  });

  /// 设置 WebViewController 并添加 JavaScript Channels
  Future<void> setController(WebViewController controller) async {
    _controller = controller;
    await _addJavaScriptChannels();
  }

  /// 添加 JavaScript Channels 到 Controller
  Future<void> _addJavaScriptChannels() async {
    if (_controller == null) {
      debugPrint('[KernelSU] Controller not set, cannot add JS channels');
      return;
    }

    // 添加 exec channel
    await _controller!.addJavaScriptChannel(
      'kernelsu_exec',
      onMessageReceived: (JavaScriptMessage message) async {
        final result = await _handleExec(message.message);
        _sendResponse(message.message, result);
      },
    );

    // 添加 toast channel
    await _controller!.addJavaScriptChannel(
      'kernelsu_toast',
      onMessageReceived: (JavaScriptMessage message) {
        debugPrint('[KernelSU] Toast: $message');
        onToast?.call(message.message);
      },
    );

    // 添加 fullscreen channel
    await _controller!.addJavaScriptChannel(
      'kernelsu_fullscreen',
      onMessageReceived: (JavaScriptMessage message) {
        final enable = message.message.toLowerCase() == 'true';
        debugPrint('[KernelSU] FullScreen: $enable');
        onFullScreen?.call(enable);
      },
    );

    // 添加 getModuleDir channel
    await _controller!.addJavaScriptChannel(
      'kernelsu_getModuleDir',
      onMessageReceived: (JavaScriptMessage message) async {
        _sendResponse(message.message, moduleDir);
      },
    );

    debugPrint('[KernelSU] JavaScript channels added');
  }

  /// 注入 KernelSU JavaScript API
  ///
  /// 在 WebView 加载完成后调用
  Future<void> injectJavaScriptAPI() async {
    if (_controller == null) {
      debugPrint('[KernelSU] Controller not set, cannot inject JS API');
      return;
    }

    final jsCode = '''
(function() {
  // KernelSU JavaScript API
  window.kernelsu = {
    // 执行命令（非 Root）
    exec: function(command, callback) {
      if (typeof callback === 'function') {
        kernelsu_exec.postMessage(JSON.stringify({
          command: command,
          callback: true
        }));
      } else {
        return new Promise((resolve, reject) => {
          const id = 'exec_' + Date.now();
          window._ksuPendingCallbacks = window._ksuPendingCallbacks || {};
          window._ksuPendingCallbacks[id] = { resolve, reject };
          kernelsu_exec.postMessage(JSON.stringify({
            command: command,
            callbackId: id
          }));
        });
      }
    },

    // 以 Root 权限执行命令
    execRoot: function(command, callback) {
      if (typeof callback === 'function') {
        kernelsu_exec.postMessage(JSON.stringify({
          command: command,
          root: true,
          callback: true
        }));
      } else {
        return new Promise((resolve, reject) => {
          const id = 'execRoot_' + Date.now();
          window._ksuPendingCallbacks = window._ksuPendingCallbacks || {};
          window._ksuPendingCallbacks[id] = { resolve, reject };
          kernelsu_exec.postMessage(JSON.stringify({
            command: command,
            root: true,
            callbackId: id
          }));
        });
      }
    },

    // 获取模块目录
    getModuleDir: function() {
      return "$moduleDir";
    },

    // 显示 Toast
    toast: function(message) {
      kernelsu_toast.postMessage(message);
    },

    // 切换全屏
    goFullScreen: function(enable) {
      kernelsu_fullscreen.postMessage(enable ? 'true' : 'false');
    },

    // 模块信息
    moduleInfo: {
      id: "$moduleId",
      path: "$moduleDir"
    }
  };

  // 兼容性：也支持旧的 API 名称
  window.ksu = window.kernelsu;

  // 处理来自 Dart 的响应
  window._ksuHandleResponse = function(callbackId, result) {
    if (window._ksuPendingCallbacks && window._ksuPendingCallbacks[callbackId]) {
      const { resolve } = window._ksuPendingCallbacks[callbackId];
      delete window._ksuPendingCallbacks[callbackId];
      if (resolve) resolve(result);
    }
  };

  console.log('[KernelSU] JavaScript API injected successfully');
})();
''';

    await _controller!.runJavaScript(jsCode);
    debugPrint('[KernelSU] JavaScript API injected');
  }

  /// 处理 exec 命令
  Future<Map<String, dynamic>> _handleExec(String message) async {
    try {
      final data = jsonDecode(message) as Map<String, dynamic>;
      final command = data['command'] as String?;
      final useRoot = data['root'] == true;

      if (command == null || command.isEmpty) {
        return {
          'errno': 1,
          'stdout': '',
          'stderr': 'Empty command',
        };
      }

      // 执行命令
      final result = await _executeCommand(command, useRoot);

      return {
        'errno': result.exitCode,
        'stdout': result.stdout,
        'stderr': result.stderr,
      };
    } catch (e) {
      return {
        'errno': 1,
        'stdout': '',
        'stderr': 'Error: $e',
      };
    }
  }

  /// 执行 Shell 命令
  Future<CommandResult> _executeCommand(String command, bool useRoot) async {
    try {
      ProcessResult result;

      if (useRoot) {
        // 使用 Root Shell 执行
        result = await Process.run(
          'su',
          ['-c', command],
        );
      } else {
        // 普通执行
        result = await Process.run(
          'sh',
          ['-c', command],
        );
      }

      return CommandResult(
        exitCode: result.exitCode as int,
        stdout: (result.stdout as String).trim(),
        stderr: (result.stderr as String).trim(),
      );
    } catch (e) {
      return CommandResult(
        exitCode: 1,
        stdout: '',
        stderr: 'Execution error: $e',
      );
    }
  }

  /// 发送响应到 WebView
  void _sendResponse(String message, dynamic result) {
    if (_controller == null) return;

    try {
      final data = jsonDecode(message) as Map<String, dynamic>;
      final callbackId = data['callbackId'] as String?;

      if (callbackId != null) {
        final jsCode =
            'window._ksuHandleResponse("$callbackId", ${jsonEncode(result)});';
        _controller!.runJavaScript(jsCode);
      }
    } catch (e) {
      debugPrint('[KernelSU] Send response error: $e');
    }
  }
}

/// 命令执行结果
class CommandResult {
  final int exitCode;
  final String stdout;
  final String stderr;

  const CommandResult({
    required this.exitCode,
    required this.stdout,
    required this.stderr,
  });

  Map<String, dynamic> toJson() => {
        'errno': exitCode,
        'stdout': stdout,
        'stderr': stderr,
      };
}

/// KernelSU JS Bridge 创建器
///
/// 用于简化 JS Bridge 的创建
class KernelSUJsBridgeBuilder {
  String? _moduleDir;
  String? _moduleId;
  void Function(String)? _onToast;
  void Function(bool)? _onFullScreen;

  KernelSUJsBridgeBuilder moduleDir(String dir) {
    _moduleDir = dir;
    return this;
  }

  KernelSUJsBridgeBuilder moduleId(String id) {
    _moduleId = id;
    return this;
  }

  KernelSUJsBridgeBuilder onToast(void Function(String) callback) {
    _onToast = callback;
    return this;
  }

  KernelSUJsBridgeBuilder onFullScreen(void Function(bool) callback) {
    _onFullScreen = callback;
    return this;
  }

  KernelSUJsBridge build() {
    if (_moduleDir == null || _moduleId == null) {
      throw ArgumentError('moduleDir and moduleId are required');
    }

    return KernelSUJsBridge(
      moduleDir: _moduleDir!,
      moduleId: _moduleId!,
      onToast: _onToast,
      onFullScreen: _onFullScreen,
    );
  }
}
