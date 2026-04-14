class MagiskStatus {
  final String versionCode;
  final bool isRooted;
  final bool isZygiskEnabled;
  final bool isRamdiskLoaded;
  final String appVersion; // MagisKube app version

  const MagiskStatus({
    required this.versionCode,
    required this.isRooted,
    required this.isZygiskEnabled,
    required this.isRamdiskLoaded,
    this.appVersion = '1.0.0',
  });

  MagiskStatus copyWith({
    String? versionCode,
    bool? isRooted,
    bool? isZygiskEnabled,
    bool? isRamdiskLoaded,
    String? appVersion,
  }) {
    return MagiskStatus(
      versionCode: versionCode ?? this.versionCode,
      isRooted: isRooted ?? this.isRooted,
      isZygiskEnabled: isZygiskEnabled ?? this.isZygiskEnabled,
      isRamdiskLoaded: isRamdiskLoaded ?? this.isRamdiskLoaded,
      appVersion: appVersion ?? this.appVersion,
    );
  }
}

class Module {
  final String name;
  final String version;
  final String author;
  final bool isEnabled;
  final String description;
  final String path;
  final bool hasWebUI;        // Whether module has web interface
  final String? webUIUrl;     // URL for web interface (e.g., http://127.0.0.1:port)
  final bool hasActionScript; // Whether module has action script (action.sh)
  final int? webUIPort;       // Port for web UI
  final bool needsReboot;     // Whether module needs reboot to take effect (update folder exists)
  final bool hasRemoveTag;    // Whether module has remove tag (/data/adb/modules/%module%/remove exists)
  final bool hasUpdateTag;   // Whether module has update tag (/data/adb/modules/%module%/update exists)

  const Module({
    required this.name,
    this.version = 'Unknown',
    this.author = 'Unknown',
    this.isEnabled = true,
    this.description = '',
    this.path = '',
    this.hasWebUI = false,
    this.webUIUrl,
    this.hasActionScript = false,
    this.webUIPort,
    this.needsReboot = false,
    this.hasRemoveTag = false,
    this.hasUpdateTag = false,
  });

  /// Check if module has any tag (remove or update)
  bool get hasAnyTag => hasRemoveTag || hasUpdateTag;

  Module copyWith({
    String? name,
    String? version,
    String? author,
    bool? isEnabled,
    String? description,
    String? path,
    bool? hasWebUI,
    String? webUIUrl,
    bool? hasActionScript,
    int? webUIPort,
    bool? needsReboot,
    bool? hasRemoveTag,
    bool? hasUpdateTag,
  }) {
    return Module(
      name: name ?? this.name,
      version: version ?? this.version,
      author: author ?? this.author,
      isEnabled: isEnabled ?? this.isEnabled,
      description: description ?? this.description,
      path: path ?? this.path,
      hasWebUI: hasWebUI ?? this.hasWebUI,
      webUIUrl: webUIUrl ?? this.webUIUrl,
      hasActionScript: hasActionScript ?? this.hasActionScript,
      webUIPort: webUIPort ?? this.webUIPort,
      needsReboot: needsReboot ?? this.needsReboot,
      hasRemoveTag: hasRemoveTag ?? this.hasRemoveTag,
      hasUpdateTag: hasUpdateTag ?? this.hasUpdateTag,
    );
  }
}

class AppInfo {
  final String name;
  final String packageName;
  final bool isActive;  // For denylist: true = not in denylist (root visible),false = in denylist (root hidden)
  final bool hasRootAccess;  // true = app has been granted root access

  const AppInfo({
    required this.name,
    required this.packageName,
    this.isActive = true,
    this.hasRootAccess = false,
  });

  AppInfo copyWith({
    String? name,
    String? packageName,
    bool? isActive,
    bool? hasRootAccess,
  }) {
    return AppInfo(
      name: name ?? this.name,
      packageName: packageName ?? this.packageName,
      isActive: isActive ?? this.isActive,
      hasRootAccess: hasRootAccess ?? this.hasRootAccess,
    );
  }
}

class Contributor {
  final String name;
  final String platform;
  final String? github;

  const Contributor({
    required this.name,
    required this.platform,
    this.github,
  });
}
