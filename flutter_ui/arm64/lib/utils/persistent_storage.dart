import 'package:shared_preferences/shared_preferences.dart';

class PersistentStorage {
  static const String _darkModeKey = 'dark_mode';
  static const String _tileColorKey = 'tile_color';

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
}
