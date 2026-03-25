import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:magiskube/providers/dashboard_providers.dart';

void main() {
  group('Theme Color Tests', () {
    test('Fallback to default color when Monet is not available', () {
      AppTheme.monetPrimary = null;
      
      final color = AppTheme.getWidgetColor(1, false);
      expect(color, const Color(0xFF009688)); // Default fallback color
    });

    test('Use Monet color when available (Android 12+ dynamic color)', () {
      AppTheme.monetPrimary = const Color(0xFF123456);
      
      final color = AppTheme.getWidgetColor(1, false);
      expect(color, const Color(0xFF123456));
    });

    test('Dark mode correctly darkens the Monet color', () {
      AppTheme.monetPrimary = const Color(0xFF009688);
      
      final color = AppTheme.getWidgetColor(1, true);
      // It should be darker than primary
      expect(color.value != 0xFF009688, true);
    });
  });
}
