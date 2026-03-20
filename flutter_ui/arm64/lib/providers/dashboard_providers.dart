import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import '../services/android_data_service.dart';
import '../utils/persistent_storage.dart';

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
    'Blue',
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

  static Color getTileWithIndex(int tileIndex, int colorIndex, bool isDark) {
    return getTileWidgetColor(tileIndex, colorIndex, isDark);
  }

  static Color getWidgetColor(int colorIndex, bool isDark) {
    if (colorIndex == 0) {
      // Default theme - Magisk green, deeper in dark mode
      return isDark ? const Color(0xFF00695C) : const Color(0xFF4DB6AC);
    }
    
    if (isDark) {
      // Dark mode: all colors should be deeper
      return _darkenColor(tileColors[colorIndex], 0.45);
    }
    return tileColors[colorIndex];
  }

  static Color getTileWidgetColor(int tileIndex, int colorIndex, bool isDark) {
    if (colorIndex == 0) {
      // Default theme
      switch (tileIndex) {
        case 0:
          // Magisk tile - dark mode 10% deeper, light mode 30% lighter
          return isDark 
            ? _darkenColor(const Color(0xFF009688), 0)
            : _lightenColor(const Color(0xFF009688), 0.10);
        case 1:
          // DenyList tile
          return isDark 
            ? _darkenColor(const Color(0xFFFFC107), 0.10) 
            : _lightenColor(const Color(0xFFFFC107), 0.20);
        case 2:
          // Contributor tile
          return isDark 
            ? _darkenColor(const Color(0xFF9C27B0), 0.10) 
            : _lightenColor(const Color(0xFF9C27B0), 0.20);
        case 3:
          // Modules tile
          return isDark 
            ? _darkenColor(const Color(0xFF2196F3), 0.10) 
            : _lightenColor(const Color(0xFF2196F3), 0.20);
        case 4:
          // Apps tile
          return isDark 
            ? _darkenColor(const Color(0xFFF44336), 0.10) 
            : _lightenColor(const Color(0xFFF44336), 0.20);
        default:
          return isDark 
            ? _darkenColor(const Color(0xFF009688), 0.10)
            : _lightenColor(const Color(0xFF009688), 0.20);
      }
    }
    
    if (isDark) {
      // Other themes in dark mode - 10% deeper
      return _darkenColor(tileColors[colorIndex], 0.10);
    }
    // Light mode - 30% lighter
    return _lightenColor(tileColors[colorIndex], 0.30);
  }

  static Color _darkenColor(Color color, double factor) {
    final hsl = HSLColor.fromColor(color);
    final newLightness = (hsl.lightness * (1 - factor)).clamp(0.0, 1.0);
    return hsl.withLightness(newLightness).toColor();
  }

  static Color _lightenColor(Color color, double factor) {
    final hsl = HSLColor.fromColor(color);
    final newLightness = (hsl.lightness + (1 - hsl.lightness) * factor).clamp(0.0, 1.0);
    return hsl.withLightness(newLightness).toColor();
  }
}

/// Cached Magisk status provider with persistent cache
final magiskStatusProvider =
    StateNotifierProvider<MagiskStatusNotifier, MagiskStatus>((ref) {
  return MagiskStatusNotifier();
});

class MagiskStatusNotifier extends StateNotifier<MagiskStatus> {
  static DateTime? _lastUpdate;
  static const _cacheDuration = Duration(seconds: 5);
  static MagiskStatus? _persistentCache; // In-memory cache for cold start
  
  MagiskStatusNotifier()
      : super(_persistentCache ?? const MagiskStatus(
          versionCode: 'Loading...',
          isRooted: false,
          isZygiskEnabled: false,
          isRamdiskLoaded: false,
        )) {
    if (_persistentCache == null) {
      _loadFromStorage();
    } else {
      // Still refresh in background
      _loadDataIfNeeded();
    }
  }

  Future<void> _loadFromStorage() async {
    final storage = PersistentStorage();
    final cached = await storage.loadMagiskStatusCache();
    if (cached.isNotEmpty) {
      final status = MagiskStatus(
        versionCode: cached['versionCode']?.toString() ?? 'Unknown',
        isRooted: cached['isRooted'] as bool? ?? false,
        isZygiskEnabled: cached['isZygiskEnabled'] as bool? ?? false,
        isRamdiskLoaded: cached['isRamdiskLoaded'] as bool? ?? false,
      );
      _persistentCache = status;
      state = status;
    }
    // Always refresh after loading cache
    _loadDataIfNeeded();
  }

  Future<void> _loadDataIfNeeded() async {
    final now = DateTime.now();
    if (_lastUpdate != null && now.difference(_lastUpdate!) < _cacheDuration) {
      return; // Use cached data
    }
    await _loadData();
  }

  Future<void> _loadData() async {
    try {
      final results = await Future.wait([
        AndroidDataService.getMagiskVersion(),
        AndroidDataService.isRooted(),
        AndroidDataService.isZygiskEnabled(),
        AndroidDataService.isRamdiskLoaded(),
      ]);

      _lastUpdate = DateTime.now();
      
      final status = MagiskStatus(
        versionCode: results[0] as String,
        isRooted: results[1] as bool,
        isZygiskEnabled: results[2] as bool,
        isRamdiskLoaded: results[3] as bool,
      );
      
      _persistentCache = status;
      state = status;
      
      // Save to persistent storage
      final storage = PersistentStorage();
      await storage.saveMagiskStatusCache({
        'versionCode': status.versionCode,
        'isRooted': status.isRooted,
        'isZygiskEnabled': status.isZygiskEnabled,
        'isRamdiskLoaded': status.isRamdiskLoaded,
      });
    } catch (e) {
      state = const MagiskStatus(
        versionCode: 'Unknown',
        isRooted: false,
        isZygiskEnabled: false,
        isRamdiskLoaded: false,
      );
    }
  }

  Future<void> refresh() async {
    _lastUpdate = null;
    await _loadData();
  }
}

/// Cached modules provider with persistent cache
final modulesProvider =
    StateNotifierProvider<ModulesNotifier, List<Module>>((ref) {
  return ModulesNotifier();
});

class ModulesNotifier extends StateNotifier<List<Module>> {
  static DateTime? _lastUpdate;
  static const _cacheDuration = Duration(seconds: 10);
  static List<Module> _persistentCache = []; // Persistent cache
  
  ModulesNotifier() : super(_persistentCache) {
    if (_persistentCache.isEmpty) {
      _loadFromStorage();
    } else {
      // Refresh in background
      _loadDataIfNeeded();
    }
  }
  
  Future<void> _loadFromStorage() async {
    final storage = PersistentStorage();
    final cached = await storage.loadModulesCache();
    if (cached.isNotEmpty) {
      final modules = cached.map((m) => Module(
        name: m['name']?.toString() ?? 'Unknown',
        version: m['version']?.toString() ?? 'Unknown',
        author: m['author']?.toString() ?? 'Unknown',
        isEnabled: m['isEnabled'] as bool? ?? false,
        description: m['description']?.toString() ?? '',
        path: m['path']?.toString() ?? '',
        hasWebUI: m['hasWebUI'] as bool? ?? false,
        webUIUrl: m['webUIUrl']?.toString(),
        hasActionScript: m['hasActionScript'] as bool? ?? false,
        webUIPort: m['webUIPort'] as int?,
        needsReboot: m['needsReboot'] as bool? ?? false,
      )).toList();
      
      _persistentCache = modules;
      state = modules;
    }
    // Always refresh
    _loadDataIfNeeded();
  }

  Future<void> _loadDataIfNeeded() async {
    final now = DateTime.now();
    if (_lastUpdate != null && now.difference(_lastUpdate!) < _cacheDuration) {
      return;
    }
    await _loadModules();
  }

  Future<void> _loadModules() async {
    try {
      final modules = await AndroidDataService.getModules();
      if (modules.isNotEmpty) {
        final loadedModules = <Module>[];
        final cacheData = <Map<String, dynamic>>[];
        
        for (final m in modules) {
          final modulePath = m['path']?.toString() ?? '';
          final details = await AndroidDataService.getModuleDetails(modulePath);
          
          final module = Module(
            name: m['name']?.toString() ?? 'Unknown',
            version: m['version']?.toString() ?? 'Unknown',
            author: m['author']?.toString() ?? 'Unknown',
            isEnabled: m['isEnabled'] as bool? ?? false,
            description: m['description']?.toString() ?? '',
            path: modulePath,
            hasWebUI: details['hasWebUI'] as bool? ?? false,
            webUIUrl: details['webUIUrl']?.toString(),
            hasActionScript: details['hasActionScript'] as bool? ?? false,
            webUIPort: details['webUIPort'] as int?,
            needsReboot: m['needsReboot'] as bool? ?? false,
          );
          
          loadedModules.add(module);
          cacheData.add({
            'name': module.name,
            'version': module.version,
            'author': module.author,
            'isEnabled': module.isEnabled,
            'description': module.description,
            'path': module.path,
            'hasWebUI': module.hasWebUI,
            'webUIUrl': module.webUIUrl,
            'hasActionScript': module.hasActionScript,
            'webUIPort': module.webUIPort,
            'needsReboot': module.needsReboot,
          });
        }
        
        _lastUpdate = DateTime.now();
        _persistentCache = loadedModules;
        state = loadedModules;
        
        // Save to storage
        final storage = PersistentStorage();
        await storage.saveModulesCache(cacheData);
      }
    } catch (e) {
      // Keep current list on error, don't clear
    }
  }

  void toggleModule(String name, bool enabled) {
    final updated = state.map((m) => m.name == name ? m.copyWith(isEnabled: enabled) : m).toList();
    _persistentCache = updated;
    state = updated;
  }
  
  Future<void> refresh() async {
    await _loadModules();
  }
}

/// SuList enabled state provider
final suListEnabledProvider = StateProvider<bool>((ref) => false);

/// Pending root access changes - tracked locally, flushed on refresh/leave
final pendingRootChangesProvider = StateProvider<Map<String, bool>>((ref) => {});

/// Cached apps provider with optimistic updates and delayed persistence
final appsProvider = StateNotifierProvider<AppsNotifier, List<AppInfo>>((ref) {
  return AppsNotifier(ref);
});

class AppsNotifier extends StateNotifier<List<AppInfo>> {
  final Ref _ref;
  static DateTime? _lastUpdate;
  static const _cacheDuration = Duration(seconds: 10);
  bool _isSuListEnabled = false;
  static List<AppInfo> _persistentCache = [];
  
  AppsNotifier(this._ref) : super(_persistentCache) {
    if (_persistentCache.isEmpty) {
      _loadFromStorage();
    } else {
      // Refresh in background
      _loadDataIfNeeded();
    }
  }
  
  Future<void> _loadFromStorage() async {
    final storage = PersistentStorage();
    
    // Load SuList state
    _isSuListEnabled = await storage.loadSuListEnabled();
    _ref.read(suListEnabledProvider.notifier).state = _isSuListEnabled;
    
    // Load cached apps
    final cached = await storage.loadAppsCache();
    if (cached.isNotEmpty) {
      final apps = cached.map((app) => AppInfo(
        name: app['name']?.toString() ?? 'Unknown',
        packageName: app['packageName']?.toString() ?? '',
        isActive: app['isActive'] as bool? ?? true,
        hasRootAccess: app['hasRootAccess'] as bool? ?? false,
      )).toList();
      
      _persistentCache = apps;
      state = apps;
    }
    
    // Always refresh after loading cache
    _loadDataIfNeeded();
  }
  
  Future<void> _loadSuListState() async {
    try {
      _isSuListEnabled = await AndroidDataService.isSuListEnabled();
      _ref.read(suListEnabledProvider.notifier).state = _isSuListEnabled;
      
      // Save to storage
      final storage = PersistentStorage();
      await storage.saveSuListEnabled(_isSuListEnabled);
    } catch (e) {
      _isSuListEnabled = false;
    }
  }

  Future<void> _loadDataIfNeeded() async {
    final now = DateTime.now();
    if (_lastUpdate != null && now.difference(_lastUpdate!) < _cacheDuration) {
      return;
    }
    await _loadApps();
  }

  Future<void> _loadApps() async {
    try {
      // Reload SuList state first
      await _loadSuListState();
      
      final apps = await AndroidDataService.getApps();
      
      if (apps.isNotEmpty) {
        // If SuList is enabled, load DenyList and invert the logic
        // SuList = DenyList反选：在DenyList中的应用可见root，不在DenyList中的应用被隐藏
        Set<String> denyListPackages = {};
        if (_isSuListEnabled) {
          try {
            // Get DenyList packages
            final denyList = await AndroidDataService.getDenyList();
            denyListPackages = denyList.toSet();
          } catch (e) {
            // Ignore error
          }
        }
        
        final loadedApps = apps.map((app) {
          final packageName = app['packageName']?.toString() ?? '';
          final inDenyList = denyListPackages.contains(packageName);
          return AppInfo(
            name: app['name']?.toString() ?? 'Unknown',
            packageName: packageName,
            isActive: app['isActive'] as bool? ?? true,
            // SuList mode: inDenyList = hasRootAccess (inverted logic)
            // Normal mode: use hasRootAccess from system
            hasRootAccess: _isSuListEnabled 
                ? inDenyList  // SuList: 在DenyList中 = 可见root
                : (app['hasRootAccess'] as bool? ?? false),
          );
        }).toList();
        
        _lastUpdate = DateTime.now();
        _persistentCache = loadedApps;
        state = loadedApps;
        
        // Save to storage
        await _saveAppsToStorage(loadedApps);
      }
    } catch (e) {
      // Keep current list on error
    }
  }
  
  Future<void> _saveAppsToStorage(List<AppInfo> apps) async {
    final storage = PersistentStorage();
    final cacheData = apps.map((app) => {
      'name': app.name,
      'packageName': app.packageName,
      'isActive': app.isActive,
      'hasRootAccess': app.hasRootAccess,
    }).toList();
    await storage.saveAppsCache(cacheData);
  }

  // Update local UI state only (no service call)
  void updateLocalRootAccess(String packageName, bool hasRootAccess) {
    // Track pending change
    final pending = Map<String, bool>.from(_ref.read(pendingRootChangesProvider));
    pending[packageName] = hasRootAccess;
    _ref.read(pendingRootChangesProvider.notifier).state = pending;
    
    // Update local state immediately for smooth UI
    final updated = state.map((app) {
      if (app.packageName == packageName) {
        return app.copyWith(hasRootAccess: hasRootAccess);
      }
      return app;
    }).toList();
    
    _persistentCache = updated;
    state = updated;
  }

  // Flush all pending changes to magisk.db - called on refresh or page leave
  Future<void> flushPendingChanges() async {
    final pending = _ref.read(pendingRootChangesProvider);
    if (pending.isEmpty) return;
    
    // Clear pending first to prevent double-flush
    _ref.read(pendingRootChangesProvider.notifier).state = {};
    
    try {
      // Refresh SuList state
      await _loadSuListState();
      
      for (final entry in pending.entries) {
        final packageName = entry.key;
        final hasRootAccess = entry.value;
        
        try {
          if (_isSuListEnabled) {
            // SuList mode = DenyList反选
            // hasRootAccess = true => 加入DenyList (可见root)
            // hasRootAccess = false => 从DenyList移除 (隐藏root)
            if (hasRootAccess) {
              await AndroidDataService.addToDenyList(packageName);
            } else {
              await AndroidDataService.removeFromDenyList(packageName);
            }
          } else {
            // Traditional mode
            if (hasRootAccess) {
              await AndroidDataService.grantRootAccessViaScript(packageName);
            } else {
              await AndroidDataService.revokeRootAccessViaScript(packageName);
            }
          }
        } catch (e) {
          // Continue with other changes even if one fails
        }
      }
      
      // Save updated state to storage
      await _saveAppsToStorage(state);
    } catch (e) {
      // Handle error silently
    }
  }

  Future<void> refresh() async {
    // Flush pending changes before refresh
    await flushPendingChanges();
    
    _lastUpdate = null;
    await _loadApps();
  }
  
  // Alias for refresh - used by secondary_pages.dart
  Future<void> refreshApps() async {
    await refresh();
  }
  
  // Toggle app root access - only update local state, don't write to DB
  Future<void> toggleApp(String packageName, bool hasRootAccess) async {
    updateLocalRootAccess(packageName, hasRootAccess);
  }
  
  // Direct toggle via script - used by secondary_pages.dart
  // Now only updates local state, flush happens on refresh/leave
  Future<void> toggleRootAccessViaScript(String packageName, bool hasRootAccess) async {
    updateLocalRootAccess(packageName, hasRootAccess);
  }
  
  // Update SuList state from external source
  void updateSuListState(bool enabled) {
    _isSuListEnabled = enabled;
    _ref.read(suListEnabledProvider.notifier).state = enabled;
    
    // Save to storage
    final storage = PersistentStorage();
    storage.saveSuListEnabled(enabled);
    
    // Reload apps to reflect new state
    _lastUpdate = null;
    _loadApps();
  }
}

/// Optimized logs provider with limited buffer
final logsProvider = StreamProvider<List<String>>((ref) {
  return AndroidDataService.getLogcatStream()
      .take(20)
      .toList()
      .asStream()
      .asyncExpand((initial) {
    final logs = <String>[...initial];
    return AndroidDataService.getLogcatStream().map((log) {
      logs.add(log);
      if (logs.length > 20) {
        logs.removeAt(0);
      }
      return List<String>.from(logs);
    });
  });
});

final denyListEnabledProvider = StateProvider<bool>((ref) => true);

final contributorsProvider = Provider<List<Contributor>>((ref) {
  return const [
    Contributor(
        name: 'SunRayEx',
        platform: 'GitHub',
        github: 'https://github.com/SunRayEx'),
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
        platform: 'runAway',
        github: 'none'),
  ];
});
