import 'dart:async';
import 'package:flutter/services.dart';

class AndroidDataService {
  static const MethodChannel _channel = MethodChannel('magisk_manager/data');

  static Future<List<String>> getModules() async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>('getModules');
      if (result != null) {
        return result.cast<String>();
      }
      return [];
    } catch (e) {
      return [];
    }
  }

  static Future<List<String>> getApps() async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>('getApps');
      if (result != null) {
        return result.cast<String>();
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
