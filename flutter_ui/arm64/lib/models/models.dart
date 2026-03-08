class MagiskStatus {
  final String versionCode;
  final bool isRooted;
  final bool isZygiskEnabled;
  final bool isRamdiskLoaded;

  const MagiskStatus({
    required this.versionCode,
    required this.isRooted,
    required this.isZygiskEnabled,
    required this.isRamdiskLoaded,
  });

  MagiskStatus copyWith({
    String? versionCode,
    bool? isRooted,
    bool? isZygiskEnabled,
    bool? isRamdiskLoaded,
  }) {
    return MagiskStatus(
      versionCode: versionCode ?? this.versionCode,
      isRooted: isRooted ?? this.isRooted,
      isZygiskEnabled: isZygiskEnabled ?? this.isZygiskEnabled,
      isRamdiskLoaded: isRamdiskLoaded ?? this.isRamdiskLoaded,
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

  const Module({
    required this.name,
    this.version = 'Unknown',
    this.author = 'Unknown',
    this.isEnabled = true,
    this.description = '',
    this.path = '',
  });

  Module copyWith({
    String? name,
    String? version,
    String? author,
    bool? isEnabled,
    String? description,
    String? path,
  }) {
    return Module(
      name: name ?? this.name,
      version: version ?? this.version,
      author: author ?? this.author,
      isEnabled: isEnabled ?? this.isEnabled,
      description: description ?? this.description,
      path: path ?? this.path,
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
