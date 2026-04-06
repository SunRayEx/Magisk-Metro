import 'dart:io';
import 'package:flutter/services.dart';

class MagiskBinaryService {
  static const MethodChannel _channel = MethodChannel('magisk_manager/magisk');
  static const MethodChannel _rootAccessChannel = MethodChannel('magisk_manager/root_access');

  static Future<bool> installMagisk(String bootImagePath) async {
    try {
      final result = await _channel.invokeMethod<bool>('installMagisk', {
        'bootImage': bootImagePath,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> uninstallMagisk({bool restoreImages = true}) async {
    try {
      final result = await _channel.invokeMethod<bool>('uninstallMagisk', {
        'restoreImages': restoreImages,
      });
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<String?> patchBootImage(String bootImagePath) async {
    try {
      final result = await _channel.invokeMethod<String>('patchBootImage', {
        'bootImage': bootImagePath,
      });
      return result;
    } catch (e) {
      return null;
    }
  }

  /// Patch boot image WITHOUT root access
  /// This allows users to patch a boot.img file on an unrooted device
  /// The patched image will be saved to Downloads directory
  /// 
  /// [bootImagePath] - Path to the boot image file (from file picker)
  /// [outputDir] - Optional output directory path
  /// Returns the path to the patched boot image, or null if failed
  static Future<String?> patchBootImageNoRoot(String bootImagePath, {String? outputDir}) async {
    try {
      final result = await _channel.invokeMethod<String>('patchBootImageNoRoot', {
        'bootImage': bootImagePath,
        'outputDir': outputDir,
      });
      return result;
    } catch (e) {
      print('patchBootImageNoRoot error: $e');
      return null;
    }
  }

  static Future<bool> updateMagiskManager() async {
    try {
      final result = await _channel.invokeMethod<bool>('updateManager');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  static Future<String> getLatestMagiskVersion() async {
    try {
      final result = await _channel.invokeMethod<String>('getLatestVersion');
      return result ?? 'Unknown';
    } catch (e) {
      return 'Unknown';
    }
  }

  static Future<String> getMagiskVersion() async {
    try {
      final process = await Process.run('magisk', ['-V']);
      return process.stdout.toString().trim();
    } catch (e) {
      return 'Unknown';
    }
  }

  static Future<Map<String, dynamic>> getDeviceInfo() async {
    try {
      final result =
          await _channel.invokeMethod<Map<dynamic, dynamic>>('getDeviceInfo');
      return Map<String, dynamic>.from(result ?? {});
    } catch (e) {
      return {};
    }
  }

  /// Request root access with MD3 system dialog
  /// Returns true if root access was granted, false otherwise
  static Future<bool> requestRootAccess() async {
    try {
      final result = await _rootAccessChannel.invokeMethod<bool>('requestRootAccess');
      return result ?? false;
    } catch (e) {
      print('requestRootAccess error: $e');
      return false;
    }
  }
}
