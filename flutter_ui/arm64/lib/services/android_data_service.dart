import 'dart:async';
import 'package:flutter/services.dart';

class AndroidDataService {
  static const MethodChannel _channel = MethodChannel('magisk_manager/data');
  static const MethodChannel _magiskChannel =
      MethodChannel('magisk_manager/magisk');
  static const MethodChannel _denyListChannel =
      MethodChannel('magisk_manager/denylist');

  static Future<List<Map<String, dynamic>>> getModules() async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>('getModules');
      if (result != null) {
        return result
            .map((item) => Map<String, dynamic>.from(item as Map))
            .toList();
      }
      return [];
    } catch (e) {
      return [];
    }
  }

  static Future<List<Map<String, dynamic>>> getApps() async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>('getApps');
      if (result != null) {
        return result
            .map((item) => Map<String, dynamic>.from(item as Map))
            .toList();
      }
      return [];
    } catch (e) {
      return [];
    }
  }

  static Future<String> getMagiskVersion() async {
    try {
      final result = await _channel.invokeMethod<String>('getMagiskVersion');
      return result ?? 'Unknown';
    } catch (e) {
      return 'Unknown';
    }
  }

  static Future<bool> isRooted() async {
    try {
      final result = await _channel.invokeMethod<bool>('isRooted');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> isZygiskEnabled() async {
    try {
      final result = await _channel.invokeMethod<bool>('isZygiskEnabled');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> isRamdiskLoaded() async {
    try {
      final result = await _channel.invokeMethod<bool>('isRamdiskLoaded');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<Map<String, dynamic>> getMagiskConfig() async {
    try {
      final result =
          await _channel.invokeMethod<Map<dynamic, dynamic>>('getMagiskConfig');
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return {};
    } catch (e) {
      return {};
    }
  }

  static Future<bool> installMagisk(String? bootImage) async {
    try {
      final result = await _magiskChannel.invokeMethod<bool>('installMagisk', {
        'bootImage': bootImage ?? '',
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> uninstallMagisk({bool restoreImages = true}) async {
    try {
      final result =
          await _magiskChannel.invokeMethod<bool>('uninstallMagisk', {
        'restoreImages': restoreImages,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<String?> patchBootImage(String bootImage) async {
    try {
      final result =
          await _magiskChannel.invokeMethod<String>('patchBootImage', {
        'bootImage': bootImage,
      });
      return result;
    } catch (e) {
      return null;
    }
  }

  static Future<bool> updateManager() async {
    try {
      final result = await _magiskChannel.invokeMethod<bool>('updateManager');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<String> getLatestVersion() async {
    try {
      final result =
          await _magiskChannel.invokeMethod<String>('getLatestVersion');
      return result ?? 'Unknown';
    } catch (e) {
      return 'Unknown';
    }
  }

  static Future<Map<String, dynamic>> getDeviceInfo() async {
    try {
      final result = await _magiskChannel
          .invokeMethod<Map<dynamic, dynamic>>('getDeviceInfo');
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return {};
    } catch (e) {
      return {};
    }
  }

  static Future<void> rebootDevice() async {
    try {
      await _magiskChannel.invokeMethod<bool>('rebootDevice');
    } catch (e) {}
  }

  static Future<void> openMagiskSettings() async {
    try {
      await _magiskChannel.invokeMethod<bool>('openMagiskSettings');
    } catch (e) {}
  }

  static Future<List<String>> getDenyList() async {
    try {
      final result =
          await _denyListChannel.invokeMethod<List<dynamic>>('getDenyList');
      if (result != null) {
        return result.cast<String>();
      }
      return [];
    } catch (e) {
      return [];
    }
  }

  static Future<bool> addToDenyList(String packageName) async {
    try {
      final result =
          await _denyListChannel.invokeMethod<bool>('addToDenyList', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> removeFromDenyList(String packageName) async {
    try {
      final result =
          await _denyListChannel.invokeMethod<bool>('removeFromDenyList', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> isInDenyList(String packageName) async {
    try {
      final result = await _denyListChannel.invokeMethod<bool>('isInDenyList', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Stream<String> getLogcatStream() {
    try {
      return EventChannel('magisk_manager/logs')
          .receiveBroadcastStream()
          .map((event) => event.toString())
          .handleError((error) {
        return '[E] Log error: $error';
      });
    } catch (e) {
      return Stream.error(e);
    }
  }
}
