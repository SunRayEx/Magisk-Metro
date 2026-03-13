import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'animated_dashboard_screen.dart';
import 'l10n/app_localizations.dart';
import 'utils/persistent_storage.dart';
import 'providers/dashboard_providers.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Load persisted settings
  final storage = PersistentStorage();
  final isDarkMode = await storage.loadDarkMode();
  final tileColor = await storage.loadTileColor();
  
  runApp(
    ProviderScope(
      overrides: [
        themeProvider.overrideWith((ref) => isDarkMode),
        tileColorProvider.overrideWith((ref) => tileColor),
      ],
      child: const MagiskDashboardApp(),
    ),
  );
}

class MagiskDashboardApp extends ConsumerWidget {
  const MagiskDashboardApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Listen to theme changes and persist them
    final isDark = ref.watch(themeProvider);
    final tileColor = ref.watch(tileColorProvider);
    
    // Persist changes when they occur
    ref.listen(themeProvider, (previous, next) {
      if (previous != next) {
        PersistentStorage().saveDarkMode(next);
      }
    });
    
    ref.listen(tileColorProvider, (previous, next) {
      if (previous != next) {
        PersistentStorage().saveTileColor(next);
      }
    });

    return MaterialApp(
      title: 'MagisKube',
      debugShowCheckedModeBanner: false,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('en', ''), // English
        Locale('zh', ''), // Chinese
      ],
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFF000000),
        colorScheme: const ColorScheme.light(
          primary: Color(0xFF000000),
          onPrimary: Color(0xFF000000),
        ),
        textTheme: _buildTextTheme(),
      // Font family for Chinese text
      fontFamily: 'SourceHanSans, Poppins',
      ),
      home: const AnimatedDashboardScreen(),
    );
  }

  TextTheme _buildTextTheme() {
    return GoogleFonts.poppinsTextTheme().apply(
      bodyColor: const Color(0xFF000000),
      displayColor: const Color(0xFF000000),
    );
  }
}
