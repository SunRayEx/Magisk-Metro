import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import '../services/android_data_service.dart';
import '../utils/persistent_storage.dart';

final themeProvider = StateProvider<bool>((ref) => false);

final isDarkModeProvider = Provider<bool>((ref) => ref.watch(themeProvider));

final tileColorProvider = StateProvider<int>((ref) => 0);

/// Provider for custom tile colors (per-tile)
final customTileColorsProvider = StateProvider<Map<int, Color>>((ref) => {});

/// Monet dynamic color providers
final monetPrimaryProvider = StateProvider<Color?>((ref) => null);
final monetSecondaryProvider = StateProvider<Color?>((ref) => null);
final monetTertiaryProvider = StateProvider<Color?>((ref) => null);
final monetSurfaceProvider = StateProvider<Color?>((ref) => null);
final monetErrorProvider = StateProvider<Color?>((ref) => null);

class AppTheme {
  // Core Monet colors from wallpaper
  static Color? monetPrimary;
  static Color? monetSecondary;
  static Color? monetTertiary;
  static Color? monetSurface;
  static Color? monetError;
  
  // Container variants for better tile colors
  static Color? monetPrimaryContainer;
  static Color? monetSecondaryContainer;
  static Color? monetTertiaryContainer;

  static const Color darkBackground = Color(0xFF000000);
  static const Color darkTile = Color(0xFF000000);
  static const Color darkFont = Colors.white;
  static const Color darkListItem = Color(0xFF000000);

  static const Color lightBackground = Colors.white;
  static const Color lightTile = Colors.white;
  static const Color lightFont = Colors.black;
  static const Color lightListItem = Colors.white;

  // Tile colors for presets (Blue, Red, Green, Purple, Yellow)
  // Indices: 0=Blue, 1=Red, 2=Green, 3=Purple, 4=Yellow
  static const List<Color> tileColors = [
    Color(0xFF2196F3), // 0: Blue
    Color(0xFFF44336), // 1: Red
    Color(0xFF4CAF50), // 2: Green
    Color(0xFF9C27B0), // 3: Purple
    Color(0xFFFFEB3B), // 4: Yellow
  ];

  static const List<String> tileColorNames = [
    'Default',   // 0
    'Monet',     // 1 (dynamic from wallpaper)
    'Custom',    // 2 (user-selected color)
    'Blue',      // 3 -> tileColors[0]
    'Red',       // 4 -> tileColors[1]
    'Green',     // 5 -> tileColors[2]
    'Purple',    // 6 -> tileColors[3]
    'Yellow',    // 7 -> tileColors[4]
  ];
  
  // Tile names for custom color assignment (5 tiles, excluding Logs)
  static const List<String> customizableTileNames = [
    'Magisk',
    'Settings', 
    'Contributor',
    'Modules',
    'Apps',
  ];
  
  // Custom theme color (user-selected) - used as fallback for custom mode
  static Color? customThemeColor;
  
  // Per-tile custom colors - map of tileIndex -> color
  static Map<int, Color> customTileColors = {};

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

  /// Get tile color with reactive custom colors from provider
  /// This is the preferred method for widgets that need to respond to custom color changes
  static Color getTileWithCustomColors(int tileIndex, int colorIndex, bool isDark, Map<int, Color> customColors) {
    if (colorIndex == 1) {
      // Monet theme - All tiles use the same primary color from wallpaper
      final baseColor = monetPrimary ?? const Color(0xFF009688);
      // Minimal adjustment - just return the color as-is or with very subtle tweak
      return baseColor;
    }
    
    if (colorIndex == 2) {
      // Custom theme - Check if this tile has an individual color assigned
      // Use the passed customColors parameter for reactivity
      if (customColors.containsKey(tileIndex)) {
        return customColors[tileIndex]!;
      }
      
      // Fall back to static customTileColors (for backwards compatibility)
      if (customTileColors.containsKey(tileIndex)) {
        return customTileColors[tileIndex]!;
      }
      
      // Fall back to global custom theme color
      return customThemeColor ?? const Color(0xFF009688);
    }

    // For other themes, use the standard method
    return getTileWidgetColor(tileIndex, colorIndex, isDark);
  }

  static Color getWidgetColor(int colorIndex, bool isDark) {
    // Handle special themes first
    if (colorIndex == 0) {
      // Default theme - Magisk green
      return const Color(0xFF009688);
    }
    if (colorIndex == 1) {
      // Monet theme - return color directly
      return monetPrimary ?? const Color(0xFF009688);
    }
    if (colorIndex == 2) {
      // Custom theme - return user color directly
      return customThemeColor ?? const Color(0xFF009688);
    }
    
    // For other themes (3-7), map to tileColors (0-4)
    // Return preset colors directly without modification
    final adjustedIndex = colorIndex - 3;
    if (adjustedIndex >= 0 && adjustedIndex < tileColors.length) {
      return tileColors[adjustedIndex];
    }
    
    // Fallback to default
    return const Color(0xFF009688);
  }

  static Color getTileWidgetColor(int tileIndex, int colorIndex, bool isDark) {
    if (colorIndex == 1) {
      // Monet theme - All tiles use the same primary color from wallpaper
      return monetPrimary ?? const Color(0xFF009688);
    }
    
    if (colorIndex == 2) {
      // Custom theme - Check if this tile has an individual color assigned
      if (customTileColors.containsKey(tileIndex)) {
        return customTileColors[tileIndex]!;
      }
      
      // Fall back to global custom theme color
      return customThemeColor ?? const Color(0xFF009688);
    }

    if (colorIndex == 0) {
      // Default theme
      switch (tileIndex) {
        case 0:
          // Magisk tile - 15% lighter than base color
          return _lightenColor(const Color(0xFF009688), 0.15);
        case 1:
          // Settings tile
          return const Color(0xFFFFC107);
        case 2:
          // Contributor tile
          return const Color(0xFF9C27B0);
        case 3:
          // Modules tile
          return const Color(0xFF2196F3);
        case 4:
          // Apps tile
          return const Color(0xFFF44336);
        default:
          return const Color(0xFF009688);
      }
    }
    
    // For other themes (3-7), map to tileColors (0-4)
    final adjustedIndex = colorIndex - 3;
    if (adjustedIndex >= 0 && adjustedIndex < tileColors.length) {
      // Return the preset color directly without modification
      return tileColors[adjustedIndex];
    }
    
    // Fallback to default
    return const Color(0xFF009688);
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

  /// Ensure the color has sufficient contrast for readability
  /// If it's too bright in light mode or too dark in dark mode, adjust it
  static Color _ensureContrast(Color color, bool isDark) {
    final luminance = color.computeLuminance();
    
    if (isDark) {
      // In dark mode, text is white. Ensure background isn't too bright
      if (luminance > 0.6) {
        return _darkenColor(color, 0.5); // Darken it more aggressively to improve contrast with white text
      }
      // Or too dark (indistinguishable from black background)
      if (luminance < 0.1) {
        return _lightenColor(color, 0.2); 
      }
    } else {
      // In light mode, text is often white inside the colored tile. Ensure background isn't too light
      if (luminance > 0.7) {
        return _darkenColor(color, 0.4); // Darken it significantly to make white text readable
      }
    }
    
    return color;
  }
}

/// Cached Magisk status provider with persistent cache
/// Optimized: Loads cache immediately, refreshes in background
final magiskStatusProvider =
    StateNotifierProvider<MagiskStatusNotifier, MagiskStatus>((ref) {
  return MagiskStatusNotifier();
});

class MagiskStatusNotifier extends StateNotifier<MagiskStatus> {
  static DateTime? _lastUpdate;
  static const _cacheDuration = Duration(seconds: 10);
  static MagiskStatus? _persistentCache; // In-memory cache for cold start
  static bool _initialized = false;
  
  MagiskStatusNotifier()
      : super(_persistentCache ?? const MagiskStatus(
          versionCode: '...',
          isRooted: false,
          isZygiskEnabled: false,
          isRamdiskLoaded: false,
        )) {
    if (!_initialized) {
      _initialized = true;
      _loadFromStorage();
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
    // Schedule background refresh without blocking UI
    Future.microtask(() => _refreshInBackground());
  }
  
  Future<void> _refreshInBackground() async {
    if (_lastUpdate != null && 
        DateTime.now().difference(_lastUpdate!) < _cacheDuration) {
      return;
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
      
      // Save to persistent storage (non-blocking)
      final storage = PersistentStorage();
      storage.saveMagiskStatusCache({
        'versionCode': status.versionCode,
        'isRooted': status.isRooted,
        'isZygiskEnabled': status.isZygiskEnabled,
        'isRamdiskLoaded': status.isRamdiskLoaded,
      });
    } catch (e) {
      if (_persistentCache == null) {
        state = const MagiskStatus(
          versionCode: 'Unknown',
          isRooted: false,
          isZygiskEnabled: false,
          isRamdiskLoaded: false,
        );
      }
    }
  }

  Future<void> refresh() async {
    _lastUpdate = null;
    await _loadData();
  }
}

/// Cached modules provider with persistent cache
/// Optimized: Non-blocking initial load, background refresh
final modulesProvider =
    StateNotifierProvider<ModulesNotifier, List<Module>>((ref) {
  return ModulesNotifier();
});

class ModulesNotifier extends StateNotifier<List<Module>> {
  static DateTime? _lastUpdate;
  static const _cacheDuration = Duration(seconds: 15);
  static List<Module> _persistentCache = []; // Persistent cache
  static bool _initialized = false;
  
  ModulesNotifier() : super(_persistentCache) {
    if (!_initialized) {
      _initialized = true;
      _loadFromStorage();
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
    // Schedule background refresh without blocking
    Future.microtask(() => _refreshInBackground());
  }
  
  Future<void> _refreshInBackground() async {
    if (_lastUpdate != null && 
        DateTime.now().difference(_lastUpdate!) < _cacheDuration) {
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
/// Optimized: Non-blocking initial load
final appsProvider = StateNotifierProvider<AppsNotifier, List<AppInfo>>((ref) {
  return AppsNotifier(ref);
});

class AppsNotifier extends StateNotifier<List<AppInfo>> {
  final Ref _ref;
  static DateTime? _lastUpdate;
  static const _cacheDuration = Duration(seconds: 15);
  bool _isSuListEnabled = false;
  static List<AppInfo> _persistentCache = [];
  static bool _initialized = false;
  
  AppsNotifier(this._ref) : super(_persistentCache) {
    if (!_initialized) {
      _initialized = true;
      _loadFromStorage();
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
    
    // Schedule background refresh without blocking UI
    Future.microtask(() => _refreshInBackground());
  }
  
  Future<void> _refreshInBackground() async {
    if (_lastUpdate != null && 
        DateTime.now().difference(_lastUpdate!) < _cacheDuration) {
      return;
    }
    await _loadApps();
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
      
      // Get installed apps
      final apps = await AndroidDataService.getApps();
      
      if (apps.isNotEmpty) {
        // Get root access apps from policies database
        Set<String> rootAccessPackages = {};
        try {
          final rootApps = await AndroidDataService.getRootAccessApps();
          rootAccessPackages = rootApps.toSet();
          debugPrint('AppsNotifier: Found ${rootAccessPackages.length} apps with root access: $rootAccessPackages');
        } catch (e) {
          debugPrint('AppsNotifier: Error getting root access apps: $e');
        }
        
        // If SuList is enabled, also load SuList whitelist
        Set<String> suListPackages = {};
        if (_isSuListEnabled) {
          try {
            suListPackages = await AndroidDataService.getSuListApps();
            debugPrint('AppsNotifier: SuList enabled, whitelist: $suListPackages');
          } catch (e) {
            debugPrint('AppsNotifier: Error getting SuList apps: $e');
          }
        }
        
        final loadedApps = apps.map((app) {
          final packageName = app['packageName']?.toString() ?? '';
          final hasRootAccess = _isSuListEnabled 
              ? suListPackages.contains(packageName)
              : rootAccessPackages.contains(packageName);
          
          return AppInfo(
            name: app['name']?.toString() ?? 'Unknown',
            packageName: packageName,
            isActive: app['isActive'] as bool? ?? true,
            hasRootAccess: hasRootAccess,
          );
        }).toList();
        
        _lastUpdate = DateTime.now();
        _persistentCache = loadedApps;
        state = loadedApps;
        
        debugPrint('AppsNotifier: Loaded ${loadedApps.length} apps, ${loadedApps.where((a) => a.hasRootAccess).length} with root access');
        
        // Save to storage
        await _saveAppsToStorage(loadedApps);
      }
    } catch (e) {
      debugPrint('AppsNotifier: Error loading apps: $e');
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
            // SuList mode
            if (hasRootAccess) {
              await AndroidDataService.addToSuList(packageName);
            } else {
              await AndroidDataService.removeFromSuList(packageName);
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

/// Logs provider - shows all Magisk logs using root shell fetch
/// Same method as original Magisk app: cat /cache/magisk.log || logcat -d -s Magisk
final logsProvider = StateNotifierProvider<LogsNotifier, AsyncValue<List<String>>>((ref) {
  return LogsNotifier();
});

class LogsNotifier extends StateNotifier<AsyncValue<List<String>>> {
  LogsNotifier() : super(const AsyncValue.loading()) {
    _loadLogs();
  }
  
  Future<void> _loadLogs() async {
    try {
      state = const AsyncValue.loading();
      // Use the same method as original Magisk app
      final logContent = await AndroidDataService.fetchMagiskLogs();
      final logs = logContent.split('\n').where((line) => line.isNotEmpty).toList();
      state = AsyncValue.data(logs);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }
  
  /// Clear Magisk logs
  Future<bool> clearLogs() async {
    try {
      final success = await AndroidDataService.clearMagiskLogs();
      if (success) {
        state = const AsyncValue.data([]);
        return true;
      }
      return false;
    } catch (e) {
      return false;
    }
  }
  
  Future<void> refresh() async {
    await _loadLogs();
  }
}

/// Filtered logs provider for dashboard tile - shows only E/W/D level logs
final filteredLogsProvider = Provider<List<String>>((ref) {
  final logsAsync = ref.watch(logsProvider);
  return logsAsync.when(
    data: (logs) => logs.where((log) => 
      log.contains('[E]') || 
      log.contains('[W]') || 
      log.contains('[D]') ||
      log.contains(' E:') ||
      log.contains(' W:') ||
      log.contains(' D:')
    ).toList(),
    loading: () => [],
    error: (_, __) => [],
  );
});

final denyListEnabledProvider = StateProvider<bool>((ref) => false);

/// DenyList state provider with caching
final denyListStateProvider = StateNotifierProvider<DenyListNotifier, DenyListState>((ref) {
  return DenyListNotifier();
});

/// DenyList state model
class DenyListState {
  final bool isEnabled;
  final Set<String> apps;
  final Set<String> activities;
  final bool isLoading;
  final DateTime? lastUpdate;
  
  const DenyListState({
    this.isEnabled = false,
    this.apps = const {},
    this.activities = const {},
    this.isLoading = true,
    this.lastUpdate,
  });
  
  DenyListState copyWith({
    bool? isEnabled,
    Set<String>? apps,
    Set<String>? activities,
    bool? isLoading,
    DateTime? lastUpdate,
  }) {
    return DenyListState(
      isEnabled: isEnabled ?? this.isEnabled,
      apps: apps ?? this.apps,
      activities: activities ?? this.activities,
      isLoading: isLoading ?? this.isLoading,
      lastUpdate: lastUpdate ?? this.lastUpdate,
    );
  }
}

/// Pending DenyList changes - tracked locally, flushed on refresh/leave
final pendingDenyListChangesProvider = StateProvider<Map<String, bool>>((ref) => {});

class DenyListNotifier extends StateNotifier<DenyListState> {
  static DenyListState? _persistentCache;
  static const _cacheDuration = Duration(seconds: 10);
  
  DenyListNotifier() : super(_persistentCache ?? const DenyListState()) {
    if (_persistentCache == null) {
      _loadFromStorage();
    } else {
      // Refresh in background
      _refreshIfNeeded();
    }
  }
  
  Future<void> _loadFromStorage() async {
    final storage = PersistentStorage();
    
    final enabled = await storage.loadDenyListEnabled();
    final apps = await storage.loadDenyListApps();
    final activities = await storage.loadDenyListActivities();
    
    final cachedState = DenyListState(
      isEnabled: enabled,
      apps: apps,
      activities: activities,
      isLoading: false,
      lastUpdate: DateTime.now(),
    );
    
    _persistentCache = cachedState;
    state = cachedState;
    
    // Refresh in background
    _refreshIfNeeded();
  }
  
  Future<void> _refreshIfNeeded() async {
    final now = DateTime.now();
    if (state.lastUpdate != null && 
        now.difference(state.lastUpdate!) < _cacheDuration) {
      return;
    }
    await _loadFromMagiskDB();
  }
  
  Future<void> _loadFromMagiskDB() async {
    try {
      final enabled = await AndroidDataService.isDenyListEnabled();
      final denyListRaw = await AndroidDataService.getDenyList();
      
      // Parse denylist - format: package|process or package/activity
      final apps = <String>{};
      final activities = <String>{};
      
      for (final item in denyListRaw) {
        if (item.contains('|')) {
          // Format: package|process
          final parts = item.split('|');
          final packageName = parts.first.trim();
          if (packageName.isNotEmpty) {
            apps.add(packageName);
          }
        } else if (item.contains('/')) {
          // Format: package/activity
          activities.add(item);
          apps.add(item.split('/').first);
        } else if (item.isNotEmpty) {
          // Plain package name
          apps.add(item);
        }
      }
      
      final newState = DenyListState(
        isEnabled: enabled,
        apps: apps,
        activities: activities,
        isLoading: false,
        lastUpdate: DateTime.now(),
      );
      
      _persistentCache = newState;
      state = newState;
      
      // Save to storage
      final storage = PersistentStorage();
      await storage.saveDenyListEnabled(enabled);
      await storage.saveDenyListApps(apps);
      await storage.saveDenyListActivities(activities);
    } catch (e) {
      debugPrint('DenyListNotifier: Error loading from magisk.db: $e');
      state = state.copyWith(isLoading: false);
    }
  }
  
  /// Toggle DenyList enabled state (writes immediately)
  Future<void> setEnabled(bool enabled) async {
    state = state.copyWith(isEnabled: enabled);
    _persistentCache = state;
    
    try {
      await AndroidDataService.setDenyListEnabled(enabled);
      
      final storage = PersistentStorage();
      await storage.saveDenyListEnabled(enabled);
    } catch (e) {
      // Revert on error
      state = state.copyWith(isEnabled: !enabled);
      _persistentCache = state;
    }
  }
  
  /// Toggle app in DenyList (local state only, flush on refresh/leave)
  void toggleApp(String packageName, bool inDenyList) {
    final newApps = Set<String>.from(state.apps);
    if (inDenyList) {
      newApps.add(packageName);
    } else {
      newApps.remove(packageName);
    }
    
    state = state.copyWith(apps: newApps);
    _persistentCache = state;
  }
  
  /// Toggle activity in DenyList (local state only)
  void toggleActivity(String fullActivityName, bool inDenyList) {
    final newActivities = Set<String>.from(state.activities);
    if (inDenyList) {
      newActivities.add(fullActivityName);
    } else {
      newActivities.remove(fullActivityName);
    }
    
    state = state.copyWith(activities: newActivities);
    _persistentCache = state;
  }
  
  /// Flush pending changes to magisk.db
  Future<void> flushPendingChanges(Map<String, bool> pendingChanges) async {
    if (pendingChanges.isEmpty) return;
    
    for (final entry in pendingChanges.entries) {
      final packageName = entry.key;
      final shouldAdd = entry.value;
      
      try {
        if (shouldAdd) {
          await AndroidDataService.addToDenyList(packageName);
        } else {
          await AndroidDataService.removeFromDenyList(packageName);
        }
      } catch (e) {
        debugPrint('DenyListNotifier: Error flushing change for $packageName: $e');
      }
    }
    
    // Save updated state
    final storage = PersistentStorage();
    await storage.saveDenyListApps(state.apps);
    await storage.saveDenyListActivities(state.activities);
  }
  
  /// Refresh from magisk.db
  Future<void> refresh() async {
    state = state.copyWith(isLoading: true);
    await _loadFromMagiskDB();
  }
}

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
