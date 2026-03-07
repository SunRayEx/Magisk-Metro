import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import '../services/android_data_service.dart';

final themeProvider = StateProvider<bool>((ref) => false);

final isDarkModeProvider = Provider<bool>((ref) => ref.watch(themeProvider));

final tileColorProvider = StateProvider<int>((ref) => 0);

class AppTheme {
  static const Color darkBackground = Color(0xFF000000);
  static const Color darkTile = Color(0xFF000000);
  static const Color darkFont = Colors.white;
  static const Color darkListItem = Color(0xFF000000);

  static const Color lightBackground = Colors.white;
  static const Color lightTile = Colors.white;
  static const Color lightFont = Colors.black;
  static const Color lightListItem = Colors.white;

  static const List<Color> tileColors = [
    Color(0xFF009688),
    Color(0xFF2196F3),
    Color(0xFFF44336),
    Color(0xFF4CAF50),
    Color(0xFF9C27B0),
    Color(0xFFFFEB3B),
    Color(0xFFFF9800),
    Color(0xFF00BCD4),
  ];

  static const List<String> tileColorNames = [
    'Default',
    'Monet Blue',
    'Red',
    'Green',
    'Purple',
    'Yellow',
    'Orange',
    'Cyan',
  ];

  static Color getBackground(bool isDark) =>
      isDark ? darkBackground : lightBackground;
  static Color getTile(bool isDark) => isDark ? darkTile : lightTile;
  static Color getFont(bool isDark) => isDark ? darkFont : lightFont;
  static Color getListItem(bool isDark) =>
      isDark ? darkListItem : lightListItem;
  static Color getListItemFont(bool isDark) => isDark ? darkFont : lightFont;

  static Color getWidgetColor(int colorIndex, bool isDark) {
    if (colorIndex == 0) {
      return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
    }
    return tileColors[colorIndex];
  }

  static Color getTileWidgetColor(int tileIndex, int colorIndex, bool isDark) {
    if (colorIndex == 0) {
      switch (tileIndex) {
        case 0:
          return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
        case 1:
          return isDark ? const Color(0xFFFFC107) : const Color(0xFFFFD54F);
        case 2:
          return isDark ? const Color(0xFF9C27B0) : const Color(0xFFBA68C8);
        case 3:
          return isDark ? const Color(0xFF4285F4) : const Color(0xFF64B5F6);
        case 4:
          return isDark ? const Color(0xFFD32F2F) : const Color(0xFFEF5350);
        default:
          return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
      }
    }
    return tileColors[colorIndex];
  }
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
      final modules = await AndroidDataService.getModules();
      if (modules.isNotEmpty) {
        state = modules
            .map((m) => Module(
                  name: m['name']?.toString() ?? 'Unknown',
                  version: m['version']?.toString() ?? 'Unknown',
                  author: m['author']?.toString() ?? 'Unknown',
                  isEnabled: m['isEnabled'] as bool? ?? false,
                ))
            .toList();
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

  Future<void> toggleModule(String name, bool enabled) async {
    state = state
        .map((m) => m.name == name
            ? Module(
                name: m.name,
                version: m.version,
                author: m.author,
                isEnabled: enabled,
              )
            : m)
        .toList();
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
      final apps = await AndroidDataService.getApps();
      if (apps.isNotEmpty) {
        state = apps
            .map((app) => AppInfo(
                  name: app['name']?.toString() ?? 'Unknown',
                  packageName: app['packageName']?.toString() ?? '',
                  isActive: app['isActive'] as bool? ?? true,
                ))
            .toList();
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

  Future<void> toggleApp(String packageName, bool active) async {
    if (active) {
      await AndroidDataService.removeFromDenyList(packageName);
    } else {
      await AndroidDataService.addToDenyList(packageName);
    }
    state = state.map((app) {
      if (app.packageName == packageName) {
        return app.copyWith(isActive: active);
      }
      return app;
    }).toList();
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
