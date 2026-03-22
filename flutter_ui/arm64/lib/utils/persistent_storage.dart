import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Unified cache manager for all persistent data
/// Handles caching for:
/// - App settings (dark mode, tile color)
/// - Apps list with root access state
/// - Magisk status
/// - Modules list
/// - SuList whitelist
/// - DenyList (packages and activities)
class PersistentStorage {
  // App Settings Keys
  static const String _darkModeKey = 'dark_mode';
  static const String _tileColorKey = 'tile_color';
  
  // Data Cache Keys
  static const String _appsCacheKey = 'apps_cache';
  static const String _magiskStatusCacheKey = 'magisk_status_cache';
  static const String _modulesCacheKey = 'modules_cache';
  static const String _suListEnabledKey = 'sulist_enabled';
  static const String _suListAppsKey = 'sulist_apps';
  static const String _denyListEnabledKey = 'denylist_enabled';
  static const String _denyListAppsKey = 'denylist_apps';
  static const String _denyListActivitiesKey = 'denylist_activities';
  
  // Persistent Operation Queue Key - tracks pending changes
  static const String _pendingOperationsKey = 'pending_operations';
  
  // Cache version for migration
  static const String _cacheVersionKey = 'cache_version';
  static const int _currentCacheVersion = 1;

  // ==================== App Settings ====================
  
  Future<void> saveDarkMode(bool isDark) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_darkModeKey, isDark);
  }

  Future<bool> loadDarkMode() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_darkModeKey) ?? false;
  }

  Future<void> saveTileColor(int colorIndex) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_tileColorKey, colorIndex);
  }

  Future<int> loadTileColor() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(_tileColorKey) ?? 0;
  }

  // ==================== Apps Cache ====================
  
  Future<void> saveAppsCache(List<Map<String, dynamic>> apps) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = jsonEncode(apps);
    await prefs.setString(_appsCacheKey, jsonString);
  }

  Future<List<Map<String, dynamic>>> loadAppsCache() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_appsCacheKey);
    if (jsonString == null || jsonString.isEmpty) {
      return [];
    }
    try {
      final List<dynamic> decoded = jsonDecode(jsonString);
      return decoded.cast<Map<String, dynamic>>();
    } catch (e) {
      return [];
    }
  }

  // ==================== Magisk Status Cache ====================
  
  Future<void> saveMagiskStatusCache(Map<String, dynamic> status) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = jsonEncode(status);
    await prefs.setString(_magiskStatusCacheKey, jsonString);
  }

  Future<Map<String, dynamic>> loadMagiskStatusCache() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_magiskStatusCacheKey);
    if (jsonString == null || jsonString.isEmpty) {
      return {};
    }
    try {
      return jsonDecode(jsonString) as Map<String, dynamic>;
    } catch (e) {
      return {};
    }
  }

  // ==================== Modules Cache ====================
  
  Future<void> saveModulesCache(List<Map<String, dynamic>> modules) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = jsonEncode(modules);
    await prefs.setString(_modulesCacheKey, jsonString);
  }

  Future<List<Map<String, dynamic>>> loadModulesCache() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_modulesCacheKey);
    if (jsonString == null || jsonString.isEmpty) {
      return [];
    }
    try {
      final List<dynamic> decoded = jsonDecode(jsonString);
      return decoded.cast<Map<String, dynamic>>();
    } catch (e) {
      return [];
    }
  }

  // ==================== SuList (Whitelist Mode) ====================
  
  Future<void> saveSuListEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_suListEnabledKey, enabled);
  }

  Future<bool> loadSuListEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_suListEnabledKey) ?? false;
  }
  
  Future<void> saveSuListApps(Set<String> apps) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_suListAppsKey, jsonEncode(apps.toList()));
  }
  
  Future<Set<String>> loadSuListApps() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_suListAppsKey);
    if (jsonString == null || jsonString.isEmpty) {
      return {};
    }
    try {
      final List<dynamic> decoded = jsonDecode(jsonString);
      return decoded.cast<String>().toSet();
    } catch (e) {
      return {};
    }
  }

  // ==================== DenyList ====================
  
  Future<void> saveDenyListEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_denyListEnabledKey, enabled);
  }

  Future<bool> loadDenyListEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_denyListEnabledKey) ?? false;
  }
  
  /// Save DenyList apps set
  Future<void> saveDenyListApps(Set<String> apps) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_denyListAppsKey, jsonEncode(apps.toList()));
  }
  
  /// Load DenyList apps set
  Future<Set<String>> loadDenyListApps() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_denyListAppsKey);
    if (jsonString == null || jsonString.isEmpty) {
      return {};
    }
    try {
      final List<dynamic> decoded = jsonDecode(jsonString);
      return decoded.cast<String>().toSet();
    } catch (e) {
      return {};
    }
  }
  
  /// Save DenyList activities set
  Future<void> saveDenyListActivities(Set<String> activities) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_denyListActivitiesKey, jsonEncode(activities.toList()));
  }
  
  /// Load DenyList activities set
  Future<Set<String>> loadDenyListActivities() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_denyListActivitiesKey);
    if (jsonString == null || jsonString.isEmpty) {
      return {};
    }
    try {
      final List<dynamic> decoded = jsonDecode(jsonString);
      return decoded.cast<String>().toSet();
    } catch (e) {
      return {};
    }
  }

  // ==================== Pending Operations Queue ====================
  
  /// Pending operations structure:
  /// {
  ///   'root_access': {'package_name': true/false, ...},
  ///   'denylist': {'package_name': true/false, ...},
  ///   'sulist': {'package_name': true/false, ...},
  /// }
  
  Future<void> savePendingOperations(Map<String, Map<String, bool>> operations) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_pendingOperationsKey, jsonEncode(operations));
  }
  
  Future<Map<String, Map<String, bool>>> loadPendingOperations() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_pendingOperationsKey);
    if (jsonString == null || jsonString.isEmpty) {
      return {
        'root_access': {},
        'denylist': {},
        'sulist': {},
      };
    }
    try {
      final Map<String, dynamic> decoded = jsonDecode(jsonString);
      return {
        'root_access': Map<String, bool>.from(decoded['root_access'] ?? {}),
        'denylist': Map<String, bool>.from(decoded['denylist'] ?? {}),
        'sulist': Map<String, bool>.from(decoded['sulist'] ?? {}),
      };
    } catch (e) {
      return {
        'root_access': {},
        'denylist': {},
        'sulist': {},
      };
    }
  }
  
  Future<void> clearPendingOperations() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_pendingOperationsKey);
  }
  
  /// Add a pending operation
  Future<void> addPendingOperation(String category, String key, bool value) async {
    final operations = await loadPendingOperations();
    operations[category] ??= {};
    operations[category]![key] = value;
    await savePendingOperations(operations);
  }
  
  /// Remove a pending operation after it's flushed
  Future<void> removePendingOperation(String category, String key) async {
    final operations = await loadPendingOperations();
    operations[category]?.remove(key);
    await savePendingOperations(operations);
  }

  // ==================== Cache Management ====================
  
  /// Check and migrate cache if needed
  Future<void> ensureCacheVersion() async {
    final prefs = await SharedPreferences.getInstance();
    final version = prefs.getInt(_cacheVersionKey) ?? 0;
    
    if (version < _currentCacheVersion) {
      // Perform migration if needed
      // For now, just update the version
      await prefs.setInt(_cacheVersionKey, _currentCacheVersion);
    }
  }
  
  /// Clear all cached data (except settings)
  Future<void> clearAllCache() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_appsCacheKey);
    await prefs.remove(_magiskStatusCacheKey);
    await prefs.remove(_modulesCacheKey);
    await prefs.remove(_suListAppsKey);
    await prefs.remove(_denyListAppsKey);
    await prefs.remove(_denyListActivitiesKey);
    await prefs.remove(_pendingOperationsKey);
  }
}
