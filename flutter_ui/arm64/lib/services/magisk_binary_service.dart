import 'dart:io';
import 'package:flutter/services.dart';

class MagiskBinaryService {
  static const MethodChannel _channel = MethodChannel('magisk_manager/magisk');

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
}
