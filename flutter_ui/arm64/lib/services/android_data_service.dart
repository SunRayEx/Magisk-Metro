import 'dart:async';
import 'package:flutter/services.dart';

class AndroidDataService {
  static const MethodChannel _channel = MethodChannel('magisk_manager/data');
  static const MethodChannel _magiskChannel =
      MethodChannel('magisk_manager/magisk');
  static const MethodChannel _denyListChannel =
      MethodChannel('magisk_manager/denylist');
  static const MethodChannel _rootAccessChannel =
      MethodChannel('magisk_manager/root_access');
  static const MethodChannel _filePickerChannel =
      MethodChannel('magisk_manager/filepicker');
  
  // Initialize app functions script on startup
  static Future<void> initialize() async {
    try {
      await _rootAccessChannel.invokeMethod<bool>('setupAppFunctionsScript');
    } catch (e) {
      // Ignore initialization errors
    }
  }

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

  static Future<bool> setZygiskEnabled(bool enabled) async {
    try {
      final result = await _channel.invokeMethod<bool>('setZygiskEnabled', {
        'enabled': enabled,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> setDenyListEnabled(bool enabled) async {
    try {
      final result = await _channel.invokeMethod<bool>('setDenyListEnabled', {
        'enabled': enabled,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> isDenyListEnabled() async {
    try {
      final result = await _channel.invokeMethod<bool>('isDenyListEnabled');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<List<String>> getAppActivities(String packageName) async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>('getAppActivities', {
        'packageName': packageName,
      });
      if (result != null) {
        return result.cast<String>();
      }
      return [];
    } catch (e) {
      return [];
    }
  }

  static Future<bool> addToDenyListActivity(String activityName) async {
    try {
      final result = await _channel.invokeMethod<bool>('addToDenyListActivity', {
        'activityName': activityName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> removeFromDenyListActivity(String activityName) async {
    try {
      final result = await _channel.invokeMethod<bool>('removeFromDenyListActivity', {
        'activityName': activityName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> isInDenyListActivity(String activityName) async {
    try {
      final result = await _channel.invokeMethod<bool>('isInDenyListActivity', {
        'activityName': activityName,
      });
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

  static Future<bool> installMagisk({String? bootImage, bool isPatchMode = false}) async {
    try {
      final result = await _magiskChannel.invokeMethod<bool>('installMagisk', {
        'bootImage': bootImage ?? '',
        'isPatchMode': isPatchMode,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> otaSlotSwitch() async {
    try {
      final result = await _magiskChannel.invokeMethod<bool>('otaSlotSwitch');
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

  static Future<bool> grantRootAccess(String packageName) async {
    try {
      final result =
          await _denyListChannel.invokeMethod<bool>('grantRootAccess', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> revokeRootAccess(String packageName) async {
    try {
      final result =
          await _denyListChannel.invokeMethod<bool>('revokeRootAccess', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> hasRootAccess(String packageName) async {
    try {
      // Get all apps and check if this package has root access
      final apps = await getApps();
      final app = apps.firstWhere(
        (app) => app['packageName'] == packageName,
        orElse: () => {'hasRootAccess': false},
      );
      return app['hasRootAccess'] as bool? ?? false;
    } catch (e) {
      return false;
    }
  }

  // ==================== Root Access Management via app_functions.sh ====================
  
  /// Get list of apps with root access granted using app_functions.sh script
  static Future<List<String>> getRootAccessApps() async {
    try {
      final result = await _rootAccessChannel.invokeMethod<List<dynamic>>('getRootAccessApps');
      if (result != null) {
        return result.cast<String>();
      }
      return [];
    } catch (e) {
      return [];
    }
  }

  /// Grant root access to an app using app_functions.sh script
  static Future<bool> grantRootAccessViaScript(String packageName) async {
    try {
      final result = await _rootAccessChannel.invokeMethod<bool>('grantRootAccess', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  /// Revoke root access from an app using app_functions.sh script
  static Future<bool> revokeRootAccessViaScript(String packageName) async {
    try {
      final result = await _rootAccessChannel.invokeMethod<bool>('revokeRootAccess', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  /// Check if an app has root access using app_functions.sh script
  static Future<bool> hasRootAccessViaScript(String packageName) async {
    try {
      final result = await _rootAccessChannel.invokeMethod<bool>('hasRootAccess', {
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  /// Get root policy for an app using app_functions.sh script
  /// Returns: 0=deny, 1=allow, 2=allow_forever, 3=allow_session
  static Future<int> getRootPolicy(String packageName) async {
    try {
      final result = await _rootAccessChannel.invokeMethod<int>('getRootPolicy', {
        'packageName': packageName,
      });
      return result ?? 0;
    } catch (e) {
      return 0;
    }
  }

  /// List all root policies using app_functions.sh script
  /// Returns: List of "package_name:policy" strings
  static Future<List<String>> listRootPolicies() async {
    try {
      final result = await _rootAccessChannel.invokeMethod<List<dynamic>>('listRootPolicies');
      if (result != null) {
        return result.cast<String>();
      }
      return [];
    } catch (e) {
      return [];
    }
  }

  static Future<String?> pickFile() async {
    try {
      final result = await _filePickerChannel.invokeMethod<String>('pickFile');
      return result;
    } catch (e) {
      return null;
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

  static Future<bool> saveLogToFile(String logContent, String filename) async {
    try {
      final result = await _filePickerChannel.invokeMethod<bool>('saveLogToFile', {
        'logContent': logContent,
        'filename': filename,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  // ==================== Module Installation ====================
  
  /// Install a Magisk module from a zip file
  /// @param zipPath The path to the module zip file
  /// @return true if installation was successful, false otherwise
  static Future<bool> installModule(String zipPath) async {
    try {
      final result = await _magiskChannel.invokeMethod<bool>('installModule', {
        'zipPath': zipPath,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }
}
