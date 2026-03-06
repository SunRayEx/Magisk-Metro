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
  final bool isEnabled;

  const Module({
    required this.name,
    this.isEnabled = true,
  });
}

class AppInfo {
  final String name;
  final bool isActive;

  const AppInfo({
    required this.name,
    this.isActive = true,
  });
}

class Contributor {
  final String name;
  final String platform;

  const Contributor({
    required this.name,
    required this.platform,
  });
}
