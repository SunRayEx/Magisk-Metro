import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'animated_dashboard_screen.dart';
import 'l10n/app_localizations.dart';
import 'utils/persistent_storage.dart';
import 'providers/dashboard_providers.dart';
import 'services/android_data_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  final storage = PersistentStorage();
  
  // Load all cached data in parallel for smooth cold start
  final results = await Future.wait([
    storage.loadDarkMode(),
    storage.loadTileColor(),
    storage.loadMagiskStatusCache(),
    storage.loadModulesCache(),
    storage.loadAppsCache(),
    storage.loadSuListEnabled(),
  ]);
  
  final isDarkMode = results[0] as bool;
  final tileColor = results[1] as int;
  final magiskStatusCache = results[2] as Map<String, dynamic>;
  final modulesCache = results[3] as List<Map<String, dynamic>>;
  final appsCache = results[4] as List<Map<String, dynamic>>;
  final suListEnabled = results[5] as bool;
  
  // Initialize AndroidDataService
  await AndroidDataService.initialize();
  
  runApp(
    ProviderScope(
      overrides: [
        themeProvider.overrideWith((ref) => isDarkMode),
        tileColorProvider.overrideWith((ref) => tileColor),
        suListEnabledProvider.overrideWith((ref) => suListEnabled),
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
