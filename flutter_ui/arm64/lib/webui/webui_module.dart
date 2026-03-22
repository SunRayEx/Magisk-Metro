import 'webui_detector.dart';

/// WebUI 模块数据模型
/// 
/// 表示一个支持 WebUI 的 Magisk 模块
class WebUIModule {
  /// 模块 ID（目录名）
  final String id;
  
  /// 模块名称
  final String name;
  
  /// 模块版本
  final String version;
  
  /// 模块作者
  final String author;
  
  /// 模块描述
  final String description;
  
  /// 模块完整路径
  final String path;
  
  /// WebUI 类型
  final WebUIType webuiType;
  
  /// 重定向端口（仅 redirect 类型）
  final int? redirectPort;
  
  const WebUIModule({
    required this.id,
    required this.name,
    required this.version,
    required this.author,
    required this.description,
    required this.path,
    required this.webuiType,
    this.redirectPort,
  });
  
  /// 获取 webroot 路径
  String get webrootPath => '$path/webroot';
  
  /// 获取 index.html 路径
  String get indexHtmlPath => '$webrootPath/index.html';
  
  /// 是否为重定向型
  bool get isRedirect => webuiType == WebUIType.redirect;
  
  /// 是否为标准型
  bool get isStandard => webuiType == WebUIType.standard;
  
  /// 复制并修改
  WebUIModule copyWith({
    String? id,
    String? name,
    String? version,
    String? author,
    String? description,
    String? path,
    WebUIType? webuiType,
    int? redirectPort,
  }) {
    return WebUIModule(
      id: id ?? this.id,
      name: name ?? this.name,
      version: version ?? this.version,
      author: author ?? this.author,
      description: description ?? this.description,
      path: path ?? this.path,
      webuiType: webuiType ?? this.webuiType,
      redirectPort: redirectPort ?? this.redirectPort,
    );
  }
  
  @override
  String toString() {
    return 'WebUIModule(id: $id, name: $name, version: $version, '
        'webuiType: $webuiType, redirectPort: $redirectPort)';
  }
  
  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is WebUIModule && other.id == id;
  }
  
  @override
  int get hashCode => id.hashCode;
}
