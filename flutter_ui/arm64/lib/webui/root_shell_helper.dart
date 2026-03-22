import 'dart:io';

/// Root Shell 辅助工具类
/// 
/// 提供通过 `su -c` 执行 Root Shell 命令的静态方法
/// 用于读取 Magisk 模块目录和文件
class RootShellHelper {
  /// Magisk 模块目录
  static const String modulesDir = '/data/adb/modules';
  
  /// 执行 Root Shell 命令
  /// 
  /// [command] 要执行的命令
  /// 返回命令执行的 stdout 输出
  static Future<String> exec(String command) async {
    try {
      final result = await Process.run('su', ['-c', command]);
      
      if (result.exitCode == 0) {
        return result.stdout?.toString() ?? '';
      }
      
      throw Exception('Command failed with exit code ${result.exitCode}: ${result.stderr}');
    } catch (e) {
      rethrow;
    }
  }
  
  /// 执行 Root Shell 命令并返回完整结果
  /// 
  /// [command] 要执行的命令
  /// 返回包含 exitCode, stdout, stderr 的 Map
  static Future<Map<String, dynamic>> execWithResult(String command) async {
    try {
      final result = await Process.run('su', ['-c', command]);
      
      return {
        'exitCode': result.exitCode,
        'stdout': result.stdout?.toString() ?? '',
        'stderr': result.stderr?.toString() ?? '',
      };
    } catch (e) {
      return {
        'exitCode': -1,
        'stdout': '',
        'stderr': e.toString(),
      };
    }
  }
  
  /// 列出目录内容
  /// 
  /// [path] 目录路径
  /// 返回目录中的文件/文件夹名称列表
  static Future<List<String>> listDir(String path) async {
    try {
      final result = await Process.run('su', ['-c', 'ls -1 "$path"']);
      
      if (result.exitCode == 0) {
        final output = result.stdout?.toString() ?? '';
        if (output.isEmpty) return [];
        
        return output
            .split('\n')
            .map((s) => s.trim())
            .where((s) => s.isNotEmpty)
            .toList();
      }
      
      return [];
    } catch (e) {
      return [];
    }
  }
  
  /// 检查文件是否存在
  /// 
  /// [path] 文件路径
  static Future<bool> fileExists(String path) async {
    try {
      final result = await Process.run('su', ['-c', '[ -f "$path" ] && echo "yes" || echo "no"']);
      
      if (result.exitCode == 0) {
        final output = result.stdout?.toString().trim() ?? '';
        return output == 'yes';
      }
      
      return false;
    } catch (e) {
      return false;
    }
  }
  
  /// 检查目录是否存在
  /// 
  /// [path] 目录路径
  static Future<bool> dirExists(String path) async {
    try {
      final result = await Process.run('su', ['-c', '[ -d "$path" ] && echo "yes" || echo "no"']);
      
      if (result.exitCode == 0) {
        final output = result.stdout?.toString().trim() ?? '';
        return output == 'yes';
      }
      
      return false;
    } catch (e) {
      return false;
    }
  }
  
  /// 读取文件内容
  /// 
  /// [path] 文件路径
  /// 返回文件内容字符串，失败返回 null
  static Future<String?> readFile(String path) async {
    try {
      final result = await Process.run('su', ['-c', 'cat "$path"']);
      
      if (result.exitCode == 0) {
        return result.stdout?.toString() ?? '';
      }
      
      return null;
    } catch (e) {
      return null;
    }
  }
  
  /// 读取二进制文件内容（Base64 编码）
  /// 
  /// [path] 文件路径
  /// 返回 Base64 解码后的字节数据
  static Future<List<int>?> readBinaryFile(String path) async {
    try {
      final result = await Process.run('su', ['-c', 'base64 "$path"']);
      
      if (result.exitCode == 0) {
        final base64Str = result.stdout?.toString().trim() ?? '';
        if (base64Str.isEmpty) return null;
        
        // Base64 解码需要导入 dart:convert
        // 这里返回字符串，调用方处理解码
        return base64Str.codeUnits;
      }
      
      return null;
    } catch (e) {
      return null;
    }
  }
  
  /// 写入文件内容
  /// 
  /// [path] 文件路径
  /// [content] 文件内容
  static Future<bool> writeFile(String path, String content) async {
    try {
      // 使用 cat 和 heredoc 写入文件
      final escapedContent = content.replaceAll("'", "'\"'\"'");
      final result = await Process.run(
        'su',
        ['-c', "echo '$escapedContent' > \"$path\""],
      );
      
      return result.exitCode == 0;
    } catch (e) {
      return false;
    }
  }
  
  /// 创建目录
  /// 
  /// [path] 目录路径
  /// [recursive] 是否递归创建父目录
  static Future<bool> mkdir(String path, {bool recursive = true}) async {
    try {
      final flag = recursive ? '-p' : '';
      final result = await Process.run('su', ['-c', 'mkdir $flag "$path"']);
      
      return result.exitCode == 0;
    } catch (e) {
      return false;
    }
  }
  
  /// 删除文件或目录
  /// 
  /// [path] 路径
  /// [recursive] 是否递归删除
  static Future<bool> delete(String path, {bool recursive = true}) async {
    try {
      final flag = recursive ? '-rf' : '-f';
      final result = await Process.run('su', ['-c', 'rm $flag "$path"']);
      
      return result.exitCode == 0;
    } catch (e) {
      return false;
    }
  }
  
  /// 获取文件大小
  /// 
  /// [path] 文件路径
  static Future<int> getFileSize(String path) async {
    try {
      final result = await Process.run('su', ['-c', 'stat -c %s "$path"']);
      
      if (result.exitCode == 0) {
        return int.tryParse(result.stdout?.toString().trim() ?? '0') ?? 0;
      }
      
      return 0;
    } catch (e) {
      return 0;
    }
  }
  
  /// 获取文件的 MIME 类型（通过文件扩展名推断）
  /// 
  /// [filename] 文件名
  static String getMimeType(String filename) {
    final extension = filename.split('.').last.toLowerCase();
    
    const mimeTypes = <String, String>{
      // 文本类型
      'html': 'text/html',
      'htm': 'text/html',
      'css': 'text/css',
      'js': 'application/javascript',
      'mjs': 'application/javascript',
      'json': 'application/json',
      'xml': 'application/xml',
      'txt': 'text/plain',
      'md': 'text/markdown',
      
      // 图片类型
      'png': 'image/png',
      'jpg': 'image/jpeg',
      'jpeg': 'image/jpeg',
      'gif': 'image/gif',
      'svg': 'image/svg+xml',
      'ico': 'image/x-icon',
      'webp': 'image/webp',
      
      // 字体类型
      'woff': 'font/woff',
      'woff2': 'font/woff2',
      'ttf': 'font/ttf',
      'otf': 'font/otf',
      
      // 音视频类型
      'mp3': 'audio/mpeg',
      'wav': 'audio/wav',
      'mp4': 'video/mp4',
      'webm': 'video/webm',
      
      // 其他类型
      'pdf': 'application/pdf',
      'wasm': 'application/wasm',
    };
    
    return mimeTypes[extension] ?? 'application/octet-stream';
  }
  
  /// 扫描所有支持 WebUI 的模块
  /// 
  /// 返回包含 webroot/index.html 的模块列表
  static Future<List<WebUIModuleInfo>> scanWebUIModules() async {
    final modules = <WebUIModuleInfo>[];
    
    try {
      // 列出所有模块目录
      final dirs = await listDir(modulesDir);
      
      for (final dirName in dirs) {
        // 跳过禁用/移除的模块
        if (dirName.endsWith('_disable') || dirName.endsWith('_remove')) {
          continue;
        }
        
        final modulePath = '$modulesDir/$dirName';
        final webrootPath = '$modulePath/webroot';
        final indexPath = '$webrootPath/index.html';
        
        // 检查 webroot/index.html 是否存在
        if (await fileExists(indexPath)) {
          // 读取 module.prop
          final propContent = await readFile('$modulePath/module.prop');
          
          modules.add(WebUIModuleInfo(
            id: dirName,
            path: modulePath,
            webrootPath: webrootPath,
            moduleProp: propContent,
          ));
        }
      }
    } catch (e) {
      // 忽略错误，返回已扫描的模块
    }
    
    return modules;
  }
}

/// WebUI 模块信息
/// 
/// 包含模块的基本路径信息
class WebUIModuleInfo {
  /// 模块 ID（目录名）
  final String id;
  
  /// 模块路径
  final String path;
  
  /// webroot 路径
  final String webrootPath;
  
  /// module.prop 内容
  final String? moduleProp;
  
  WebUIModuleInfo({
    required this.id,
    required this.path,
    required this.webrootPath,
    this.moduleProp,
  });
  
  /// 从 module.prop 解析属性
  String? getProp(String key) {
    if (moduleProp == null) return null;
    
    for (final line in moduleProp!.split('\n')) {
      if (line.startsWith('$key=')) {
        return line.substring(key.length + 1).trim();
      }
    }
    
    return null;
  }
  
  /// 获取模块名称
  String get name => getProp('name') ?? id;
  
  /// 获取模块版本
  String get version => getProp('version') ?? 'Unknown';
  
  /// 获取模块作者
  String get author => getProp('author') ?? 'Unknown';
  
  /// 获取模块描述
  String get description => getProp('description') ?? '';
}
