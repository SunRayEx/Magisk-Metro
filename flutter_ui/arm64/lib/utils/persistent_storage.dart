import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

class PersistentStorage {
  static const String _darkModeKey = 'dark_mode';
  static const String _tileColorKey = 'tile_color';
  static const String _appsCacheKey = 'apps_cache';
  static const String _magiskStatusCacheKey = 'magisk_status_cache';
  static const String _modulesCacheKey = 'modules_cache';
  static const String _suListEnabledKey = 'sulist_enabled';

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

  // Apps cache - store as JSON string
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

  // Magisk status cache
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

  // Modules cache
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

  // SuList enabled state
  Future<void> saveSuListEnabled(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_suListEnabledKey, enabled);
  }

  Future<bool> loadSuListEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_suListEnabledKey) ?? false;
  }
}
