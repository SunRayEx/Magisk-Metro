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

  const Module({
    required this.name,
    this.version = 'Unknown',
    this.author = 'Unknown',
    this.isEnabled = true,
    this.description = '',
  });

  Module copyWith({
    String? name,
    String? version,
    String? author,
    bool? isEnabled,
    String? description,
  }) {
    return Module(
      name: name ?? this.name,
      version: version ?? this.version,
      author: author ?? this.author,
      isEnabled: isEnabled ?? this.isEnabled,
      description: description ?? this.description,
    );
  }
}

class AppInfo {
  final String name;
  final String packageName;
  final bool isActive;

  const AppInfo({
    required this.name,
    required this.packageName,
    this.isActive = true,
  });

  AppInfo copyWith({
    String? name,
    String? packageName,
    bool? isActive,
  }) {
    return AppInfo(
      name: name ?? this.name,
      packageName: packageName ?? this.packageName,
      isActive: isActive ?? this.isActive,
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
