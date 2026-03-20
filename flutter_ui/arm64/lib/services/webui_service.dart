import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

/// WebUI Service - Implements KernelSU WebUI compatible interface
/// Based on https://github.com/5ec1cff/WebUIStandalone
/// 
/// This service provides:
/// 1. JavaScript interface for WebView (ksu object)
/// 2. File system access to module webroot via root
/// 3. Command execution with callbacks
class WebUIService {
  static const MethodChannel _channel = MethodChannel('magisk_manager/webui');
  
  /// JavaScript interface name - matches KernelSU's "ksu"
  static const String jsInterfaceName = 'ksu';
  
  /// Domain for WebView asset loader
  static const String webuiDomain = 'mui.kernelsu.org';
  
  late WebViewController _controller;
  String _moduleDir;
  String _moduleId;
  bool _isInitialized = false;
  
  /// Callback for log messages
  Function(String)? onLog;
  
  WebUIService({
    required String moduleDir,
    required String moduleId,
    this.onLog,
  }) : _moduleDir = moduleDir,
       _moduleId = moduleId;
  
  /// Initialize the WebUI service
  Future<bool> initialize() async {
    try {
      _log('[WebUI] Initializing WebUI service for module: $_moduleId');
      
      // Setup native WebUI interface
      final result = await _channel.invokeMethod('setupWebUI', {
        'moduleDir': _moduleDir,
        'moduleId': _moduleId,
      });
      
      _isInitialized = result == true;
      _log('[WebUI] Initialization result: $_isInitialized');
      
      return _isInitialized;
    } catch (e) {
      _log('[WebUI] Initialization error: $e');
      return false;
    }
  }
  
  /// Configure WebViewController with WebUI JavaScript interface
  void configureWebViewController(WebViewController controller) {
    _controller = controller;
    
    // Add JavaScript channels for communication
    _setupJavaScriptChannels();
  }
  
  /// Setup JavaScript channels for WebView communication
  void _setupJavaScriptChannels() {
    // Channel for exec command (sync)
    _controller.addJavaScriptChannel(
      'KsuExecSync',
      onMessageReceived: (message) => _handleExecSync(message.message),
    );
    
    // Channel for exec command (async with callback)
    _controller.addJavaScriptChannel(
      'KsuExecAsync',
      onMessageReceived: (message) => _handleExecAsync(message.message),
    );
    
    // Channel for spawn command (streaming output)
    _controller.addJavaScriptChannel(
      'KsuSpawn',
      onMessageReceived: (message) => _handleSpawn(message.message),
    );
    
    // Channel for toast
    _controller.addJavaScriptChannel(
      'KsuToast',
      onMessageReceived: (message) => _handleToast(message.message),
    );
    
    // Channel for fullscreen toggle
    _controller.addJavaScriptChannel(
      'KsuFullScreen',
      onMessageReceived: (message) => _handleFullScreen(message.message),
    );
    
    // Channel for module info
    _controller.addJavaScriptChannel(
      'KsuModuleInfo',
      onMessageReceived: (message) => _handleModuleInfo(),
    );
  }
  
  /// Handle synchronous exec command
  Future<void> _handleExecSync(String message) async {
    try {
      final data = jsonDecode(message);
      final cmd = data['cmd'] as String?;
      
      if (cmd == null) return;
      
      final result = await _executeCommand(cmd);
      
      // Send result back to WebView
      await _controller.runJavaScript(
        'window._ksuExecSyncResult = ${jsonEncode(result)};',
      );
    } catch (e) {
      _log('[WebUI] Exec sync error: $e');
    }
  }
  
  /// Handle asynchronous exec command with callback
  Future<void> _handleExecAsync(String message) async {
    try {
      final data = jsonDecode(message);
      final cmd = data['cmd'] as String?;
      final callbackFunc = data['callback'] as String?;
      final options = data['options'] as Map<String, dynamic>?;
      
      if (cmd == null || callbackFunc == null) return;
      
      final result = await _executeCommandWithOptions(cmd, options);
      
      // Call JavaScript callback with result
      final jsCode = '''
        (function() {
          try {
            ${callbackFunc}(${result.exitCode}, ${jsonEncode(result.stdout)}, ${jsonEncode(result.stderr)});
          } catch(e) {
            console.error('Callback error:', e);
          }
        })();
      ''';
      
      await _controller.runJavaScript(jsCode);
    } catch (e) {
      _log('[WebUI] Exec async error: $e');
    }
  }
  
  /// Handle spawn command (streaming output)
  Future<void> _handleSpawn(String message) async {
    try {
      final data = jsonDecode(message);
      final command = data['command'] as String?;
      final args = data['args'] as List?;
      final options = data['options'] as Map<String, dynamic>?;
      final callbackFunc = data['callback'] as String?;
      
      if (command == null || callbackFunc == null) return;
      
      await _spawnCommand(command, args ?? [], options, callbackFunc);
    } catch (e) {
      _log('[WebUI] Spawn error: $e');
    }
  }
  
  /// Handle toast request
  Future<void> _handleToast(String message) async {
    // Toast is handled via JavaScript channel callback
    // The Flutter side should listen for this
    _log('[WebUI] Toast: $message');
  }
  
  /// Handle fullscreen toggle
  Future<void> _handleFullScreen(String message) async {
    try {
      final enable = message.toLowerCase() == 'true';
      await _channel.invokeMethod('setFullScreen', {'enable': enable});
    } catch (e) {
      _log('[WebUI] Fullscreen error: $e');
    }
  }
  
  /// Handle module info request
  Future<void> _handleModuleInfo() async {
    try {
      final info = {
        'moduleDir': _moduleDir,
        'id': _moduleId,
      };
      
      await _controller.runJavaScript(
        'window._ksuModuleInfo = ${jsonEncode(info)};',
      );
    } catch (e) {
      _log('[WebUI] Module info error: $e');
    }
  }
  
  /// Execute a command synchronously
  Future<String> _executeCommand(String cmd) async {
    try {
      final result = await _channel.invokeMethod('execCommand', {
        'command': cmd,
      });
      return result ?? '';
    } catch (e) {
      _log('[WebUI] Command execution error: $e');
      return '';
    }
  }
  
  /// Execute a command with options
  Future<_CommandResult> _executeCommandWithOptions(
    String cmd,
    Map<String, dynamic>? options,
  ) async {
    try {
      // Build final command with options
      final finalCmd = StringBuffer();
      
      if (options != null) {
        // Handle cwd option
        final cwd = options['cwd'] as String?;
        if (cwd != null && cwd.isNotEmpty) {
          finalCmd.write('cd $cwd;');
        }
        
        // Handle env options
        final env = options['env'] as Map<String, dynamic>?;
        if (env != null) {
          env.forEach((key, value) {
            finalCmd.write('export $key=$value;');
          });
        }
      }
      
      finalCmd.write(cmd);
      
      final result = await _channel.invokeMethod('execCommandWithResult', {
        'command': finalCmd.toString(),
      });
      
      return _CommandResult(
        exitCode: result['exitCode'] ?? 1,
        stdout: result['stdout'] ?? '',
        stderr: result['stderr'] ?? '',
      );
    } catch (e) {
      _log('[WebUI] Command execution error: $e');
      return _CommandResult(
        exitCode: 1,
        stdout: '',
        stderr: e.toString(),
      );
    }
  }
  
  /// Spawn a command with streaming output
  Future<void> _spawnCommand(
    String command,
    List args,
    Map<String, dynamic>? options,
    String callbackFunc,
  ) async {
    try {
      // Build final command
      final finalCmd = StringBuffer();
      
      if (options != null) {
        final cwd = options['cwd'] as String?;
        if (cwd != null && cwd.isNotEmpty) {
          finalCmd.write('cd $cwd;');
        }
        
        final env = options['env'] as Map<String, dynamic>?;
        if (env != null) {
          env.forEach((key, value) {
            finalCmd.write('export $key=$value;');
          });
        }
      }
      
      finalCmd.write(command);
      if (args.isNotEmpty) {
        finalCmd.write(' ');
        finalCmd.write(args.join(' '));
      }
      
      // Use EventChannel for streaming output
      // For now, execute and send all output at once
      final result = await _channel.invokeMethod('spawnCommand', {
        'command': finalCmd.toString(),
        'callbackId': callbackFunc,
      });
      
      // Send stdout data
      if (result['stdout'] != null) {
        await _emitData(callbackFunc, 'stdout', result['stdout']);
      }
      
      // Send stderr data
      if (result['stderr'] != null) {
        await _emitData(callbackFunc, 'stderr', result['stderr']);
      }
      
      // Send exit event
      await _controller.runJavaScript('''
        (function() {
          try {
            ${callbackFunc}.emit('exit', ${result['exitCode'] ?? 0});
          } catch(e) {
            console.error('emitExit error:', e);
          }
        })();
      ''');
    } catch (e) {
      _log('[WebUI] Spawn error: $e');
    }
  }
  
  /// Emit data event to JavaScript callback
  Future<void> _emitData(String callbackFunc, String streamName, String data) async {
    await _controller.runJavaScript('''
      (function() {
        try {
          ${callbackFunc}.${streamName}.emit('data', ${jsonEncode(data)});
        } catch(e) {
          console.error('emitData error:', e);
        }
      })();
    ''');
  }
  
  /// Read file from module webroot
  Future<String?> readWebrootFile(String relativePath) async {
    try {
      final result = await _channel.invokeMethod('readWebrootFile', {
        'moduleDir': _moduleDir,
        'relativePath': relativePath,
      });
      return result;
    } catch (e) {
      _log('[WebUI] Read file error: $e');
      return null;
    }
  }
  
  /// Check if module has webroot directory
  Future<bool> hasWebroot() async {
    try {
      final result = await _channel.invokeMethod('hasWebroot', {
        'moduleDir': _moduleDir,
      });
      return result == true;
    } catch (e) {
      return false;
    }
  }
  
  /// Get WebUI URL for loading
  String getWebUIUrl() {
    return 'https://$webuiDomain/index.html';
  }
  
  /// Inject KSU JavaScript interface into WebView
  Future<void> injectJavaScriptInterface() async {
    // This JavaScript creates a compatible interface matching KernelSU's ksu object
    final jsInterface = '''
      // KernelSU WebUI JavaScript Interface
      (function() {
        window.ksu = {
          // Execute command synchronously
          exec: function(cmd, callback, options) {
            if (typeof callback === 'function') {
              // Async mode with callback
              var callbackId = '_ksu_cb_' + Date.now();
              var cbObj = {
                stdout: { emit: function(event, data) { console.log('stdout:', data); } },
                stderr: { emit: function(event, data) { console.log('stderr:', data); } },
                emit: function(event, data) { 
                  if (event === 'exit') {
                    try { callback(data, null, null); } catch(e) {}
                  }
                }
              };
              window.KsuExecAsync.postMessage(JSON.stringify({
                cmd: cmd,
                callback: callback.name || 'function(r,e,o) { callback(r,e,o); }',
                options: options
              }));
              return '';
            } else {
              // Sync mode
              window.KsuExecSync.postMessage(JSON.stringify({ cmd: cmd }));
              return window._ksuExecSyncResult || '';
            }
          },
          
          // Spawn command with streaming output
          spawn: function(command, args, options, callbackFunc) {
            window.KsuSpawn.postMessage(JSON.stringify({
              command: command,
              args: args,
              options: options,
              callback: callbackFunc
            }));
          },
          
          // Show toast message
          toast: function(msg) {
            window.KsuToast.postMessage(msg);
          },
          
          // Toggle fullscreen mode
          fullScreen: function(enable) {
            window.KsuFullScreen.postMessage(enable ? 'true' : 'false');
          },
          
          // Get module info
          moduleInfo: function() {
            window.KsuModuleInfo.postMessage('');
            return window._ksuModuleInfo || { moduleDir: '', id: '' };
          }
        };
        
        // Also expose as KernelSU for compatibility
        window.KernelSU = window.ksu;
      })();
    ''';
    
    await _controller.runJavaScript(jsInterface);
    _log('[WebUI] JavaScript interface injected');
  }
  
  void _log(String message) {
    debugPrint(message);
    onLog?.call(message);
  }
  
  /// Dispose resources
  void dispose() {
    _isInitialized = false;
  }
}

/// Command execution result
class _CommandResult {
  final int exitCode;
  final String stdout;
  final String stderr;
  
  _CommandResult({
    required this.exitCode,
    required this.stdout,
    required this.stderr,
  });
}

/// WebUI JavaScript interface constants
class WebUIConstants {
  /// KernelSU WebUI domain
  static const String webuiDomain = 'mui.kernelsu.org';
  
  /// JavaScript interface name
  static const String interfaceName = 'ksu';
  
  /// Default WebUI port
  static const int defaultPort = 8080;
  
  /// Webroot directory name
  static const String webrootDir = 'webroot';
  
  /// Index file name
  static const String indexFile = 'index.html';
}
