import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import '../services/android_data_service.dart';

final themeProvider = StateProvider<bool>((ref) => false);

final isDarkModeProvider = Provider<bool>((ref) => ref.watch(themeProvider));

class AppTheme {
  static const Color darkBackground = Color(0xFF000000);
  static const Color darkTile = Color(0xFF1A1A1A);
  static const Color darkFont = Colors.white;
  static const Color darkListItem = Color(0xFF1A1A1A);

  static const Color lightBackground = Color(0xFFE0E0E0);
  static const Color lightTile = Color(0xFFE0E0E0);
  static const Color lightFont = Colors.black;
  static const Color lightListItem = Colors.white;

  static Color getBackground(bool isDark) =>
      isDark ? darkBackground : lightBackground;
  static Color getTile(bool isDark) => isDark ? darkTile : lightTile;
  static Color getFont(bool isDark) => isDark ? darkFont : lightFont;
  static Color getListItem(bool isDark) =>
      isDark ? darkListItem : lightListItem;
  static Color getListItemFont(bool isDark) => isDark ? darkFont : lightFont;
}

final magiskStatusProvider =
    StateNotifierProvider<MagiskStatusNotifier, MagiskStatus>((ref) {
  return MagiskStatusNotifier();
});

class MagiskStatusNotifier extends StateNotifier<MagiskStatus> {
  MagiskStatusNotifier()
      : super(const MagiskStatus(
          versionCode: 'Loading...',
          isRooted: false,
          isZygiskEnabled: false,
          isRamdiskLoaded: false,
        )) {
    _loadData();
  }

  Future<void> _loadData() async {
    try {
      final version = await AndroidDataService.getMagiskVersion();
      final rooted = await AndroidDataService.isRooted();
      final zygisk = await AndroidDataService.isZygiskEnabled();
      final ramdisk = await AndroidDataService.isRamdiskLoaded();

      state = MagiskStatus(
        versionCode: version,
        isRooted: rooted,
        isZygiskEnabled: zygisk,
        isRamdiskLoaded: ramdisk,
      );
    } catch (e) {
      state = const MagiskStatus(
        versionCode: 'Unknown',
        isRooted: false,
        isZygiskEnabled: false,
        isRamdiskLoaded: false,
      );
    }
  }

  void updateStatus({
    String? versionCode,
    bool? isRooted,
    bool? isZygiskEnabled,
    bool? isRamdiskLoaded,
  }) {
    state = state.copyWith(
      versionCode: versionCode,
      isRooted: isRooted,
      isZygiskEnabled: isZygiskEnabled,
      isRamdiskLoaded: isRamdiskLoaded,
    );
  }
}

final modulesProvider =
    StateNotifierProvider<ModulesNotifier, List<Module>>((ref) {
  return ModulesNotifier();
});

class ModulesNotifier extends StateNotifier<List<Module>> {
  ModulesNotifier() : super([]) {
    _loadModules();
  }

  Future<void> _loadModules() async {
    try {
      final moduleNames = await AndroidDataService.getModules();
      if (moduleNames.isNotEmpty) {
        state = moduleNames.map((name) => Module(name: name)).toList();
      }
    } catch (e) {
      // Keep empty list on error
    }
  }

  void addModule(Module module) {
    state = [...state, module];
  }

  void removeModule(String name) {
    state = state.where((m) => m.name != name).toList();
  }
}

final appsProvider = StateNotifierProvider<AppsNotifier, List<AppInfo>>((ref) {
  return AppsNotifier();
});

class AppsNotifier extends StateNotifier<List<AppInfo>> {
  AppsNotifier() : super([]) {
    _loadApps();
  }

  Future<void> _loadApps() async {
    try {
      final appNames = await AndroidDataService.getApps();
      if (appNames.isNotEmpty) {
        state = appNames.map((name) => AppInfo(name: name)).toList();
      }
    } catch (e) {
      // Keep empty list on error
    }
  }

  void addApp(AppInfo app) {
    state = [...state, app];
  }

  void removeApp(String name) {
    state = state.where((a) => a.name != name).toList();
  }
}

final logsProvider = StreamProvider<List<String>>((ref) {
  final controller = LogStreamController();
  ref.onDispose(() => controller.dispose());
  return controller.stream;
});

class LogStreamController {
  final StreamController<List<String>> _controller =
      StreamController<List<String>>();
  StreamSubscription? _subscription;
  final List<String> _logs = [];

  LogStreamController() {
    _startRealLogs();
  }

  void _startRealLogs() {
    try {
      _subscription = AndroidDataService.getLogcatStream().listen(
        (log) {
          _logs.add(log);
          if (_logs.length > 20) {
            _logs.removeAt(0);
          }
          _controller.add(List.from(_logs));
        },
        onError: (error) {
          _controller.addError(error);
        },
      );
    } catch (e) {
      // If real logs fail, add fallback
      _logs.add('[E] Log service unavailable');
      _controller.add(List.from(_logs));
    }
  }

  Stream<List<String>> get stream => _controller.stream;

  void dispose() {
    _subscription?.cancel();
    _controller.close();
  }
}

final denyListEnabledProvider = StateProvider<bool>((ref) => true);

final contributorsProvider = Provider<List<Contributor>>((ref) {
  return const [
    Contributor(
        name: 'topjohnwu',
        platform: 'GitHub',
        github: 'https://github.com/topjohnwu'),
    Contributor(
        name: 'vvb2060',
        platform: 'GitHub',
        github: 'https://github.com/vvb2060'),
    Contributor(
        name: '[HuskyDG]',
        platform: 'GitHub',
        github: 'https://github.com/HuskyDG'),
  ];
});
