import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';

final magiskStatusProvider =
    StateNotifierProvider<MagiskStatusNotifier, MagiskStatus>((ref) {
  return MagiskStatusNotifier();
});

class MagiskStatusNotifier extends StateNotifier<MagiskStatus> {
  MagiskStatusNotifier()
      : super(const MagiskStatus(
          versionCode: '30060',
          isRooted: true,
          isZygiskEnabled: true,
          isRamdiskLoaded: true,
        ));

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
  ModulesNotifier()
      : super(const [
          Module(name: 'LSPosed - Irena', isEnabled: true),
          Module(name: 'Tricky Store', isEnabled: true),
          Module(name: 'Play Integrity Fix', isEnabled: true),
          Module(name: 'Shamiko', isEnabled: true),
          Module(name: 'Storage Redirect', isEnabled: true),
          Module(name: 'Universal SafetyNet Fix', isEnabled: true),
          Module(name: 'MiuiNativeHelper', isEnabled: true),
          Module(name: 'XPrivacyLua', isEnabled: true),
        ]);

  void addModule(Module module) {
    state = [...state, module];
  }

  void removeModule(String name) {
    state = state.where((m) => m.name != name).toList();
  }
}

final appsProvider =
    StateNotifierProvider<AppsNotifier, List<AppInfo>>((ref) {
  return AppsNotifier();
});

class AppsNotifier extends StateNotifier<List<AppInfo>> {
  AppsNotifier()
      : super(const [
          AppInfo(name: 'IceBox', isActive: true),
          AppInfo(name: 'Scene', isActive: true),
          AppInfo(name: '爱玩机工具箱', isActive: true),
          AppInfo(name: 'Island', isActive: true),
          AppInfo(name: 'Leo', isActive: true),
          AppInfo(name: 'Scene', isActive: true),
          AppInfo(name: 'Storage Isolation', isActive: true),
          AppInfo(name: 'App Manager', isActive: true),
          AppInfo(name: 'Systemizer', isActive: true),
          AppInfo(name: 'Wsm', isActive: true),
        ]);

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

class LogStreamController extends StreamController<List<String>> {
  late final Timer _timer;
  final List<String> _logs = [];
  int _counter = 0;

  static const List<String> _logTemplates = [
    '[E] Too Much Error! Something went wrong...',
    '[W] Warning: Low memory detected',
    '[I] Magisk: Zygisk started successfully',
    '[D] Boot: Ramdisk patch applied',
    '[E] Failed to load module: LSPosed',
    '[I] Manager: Checking for updates...',
    '[D] SQL: Database initialized',
    '[W] SafetyNet: Hardware attestation failed',
    '[E] SELinux: Policy enforcement active',
    '[I] Core: All patches applied',
    '[D] Zygote: Forked new process',
    '[E] I Don\'t Wanna Type!',
  ];

  LogStreamController() : super() {
    _addLog();
    _addLog();
    _addLog();
    _timer = Timer.periodic(const Duration(milliseconds: 800), (_) {
      _addLog();
    });
  }

  void _addLog() {
    final template = _logTemplates[_counter % _logTemplates.length];
    _logs.add('$template [${DateTime.now().millisecondsSinceEpoch % 10000}]');
    if (_logs.length > 20) {
      _logs.removeAt(0);
    }
    _counter++;
    add(List.from(_logs));
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }
}

final denyListEnabledProvider = StateProvider<bool>((ref) => true);

final contributorsProvider = Provider<List<Contributor>>((ref) {
  return const [
    Contributor(name: 'topjohnwu', platform: 'GitHub'),
    Contributor(name: 'vvb2060', platform: 'GitHub'),
    Contributor(name: '[HuskyDG]', platform: 'none'),
  ];
});
