import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:magiskube/providers/dashboard_providers.dart';

void main() {
  group('Monet Dynamic Color Palette Extraction Tests', () {
    test('Extracts varied hues for different tiles based on primary color', () {
      // Set a known primary color (Red: Hue 0)
      AppTheme.monetPrimary = const Color(0xFFFF0000); 
      
      // Magisk Tile (0) -> Base color
      final magiskColor = AppTheme.getTileWidgetColor(0, 1, false);
      
      // DenyList Tile (1) -> Analogous/Triadic (+30 hue)
      final denyListColor = AppTheme.getTileWidgetColor(1, 1, false);
      
      // Contributor Tile (2) -> Complementary/Triadic (+150 hue)
      final contributorColor = AppTheme.getTileWidgetColor(2, 1, false);
      
      // Modules Tile (3) -> Analogous (-30 hue / 330 hue)
      final modulesColor = AppTheme.getTileWidgetColor(3, 1, false);
      
      // Apps Tile (4) -> Complementary (+180 hue)
      final appsColor = AppTheme.getTileWidgetColor(4, 1, false);
      
      // They should all be distinct colors to provide visual hierarchy
      final colors = {
        magiskColor.value,
        denyListColor.value,
        contributorColor.value,
        modulesColor.value,
        appsColor.value,
      };
      
      // We expect 5 different colors
      expect(colors.length, 5);
    });

    test('Ensures contrast in Light Mode for high luminance colors', () {
      // Very bright yellow (High luminance)
      AppTheme.monetPrimary = const Color(0xFFFFFF00); 
      
      // Original color is too bright for white text, the function should darken it
      final adjustedColor = AppTheme.getTileWidgetColor(0, 1, false);
      
      // The adjusted color should have lower luminance than the original
      expect(adjustedColor.computeLuminance(), lessThan(const Color(0xFFFFFF00).computeLuminance()));
      
      // It should not be the exact same as the lightly lightened original (which is what happens to normal colors)
      // Normal behavior: _lightenColor(color, 0.20)
      // Contrast adjustment for bright: _darkenColor(color, 0.4)
      final hsl = HSLColor.fromColor(adjustedColor);
      expect(hsl.lightness, lessThan(0.6)); // Should be darkened enough for white text
    });

    test('Ensures contrast in Dark Mode for very bright colors', () {
      // Very bright white/cyan
      AppTheme.monetPrimary = const Color(0xFFE0FFFF); 
      
      // Dark mode normally darkens by 0.10. But if it's still too bright (>0.6 luminance), it darkens further by 0.3
      final adjustedColorDark = AppTheme.getTileWidgetColor(0, 1, true);
      
      expect(adjustedColorDark.computeLuminance(), lessThan(0.6));
    });
    
    test('Ensures contrast in Dark Mode for very dark colors', () {
      // Very dark blue
      AppTheme.monetPrimary = const Color(0xFF000033); 
      
      // Dark mode normally darkens by 0.10. But if it's too dark (<0.1 luminance), it lightens by 0.2
      final adjustedColorDark = AppTheme.getTileWidgetColor(0, 1, true);
      
      // It should be lightened to be visible against black background
      expect(adjustedColorDark.computeLuminance(), greaterThan(0.01)); 
    });
  });
}
