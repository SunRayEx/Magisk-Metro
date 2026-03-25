import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'animated_dashboard_screen.dart';
import 'l10n/app_localizations.dart';
import 'utils/persistent_storage.dart';
import 'providers/dashboard_providers.dart';
import 'services/android_data_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: MagiskDashboardApp()));
}

class MagiskDashboardApp extends ConsumerStatefulWidget {
  const MagiskDashboardApp({super.key});

  @override
  ConsumerState<MagiskDashboardApp> createState() => _MagiskDashboardAppState();
}

class _MagiskDashboardAppState extends ConsumerState<MagiskDashboardApp> {
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    final storage = PersistentStorage();
    
    // Load all cached data in parallel for smooth cold start
    final results = await Future.wait([
      storage.loadDarkMode(),
      storage.loadTileColor(),
      storage.loadMagiskStatusCache(),
      storage.loadModulesCache(),
      storage.loadAppsCache(),
      storage.loadSuListEnabled(),
      storage.loadCustomTileColors(),
    ]);
    
    final isDarkMode = results[0] as bool;
    final tileColor = results[1] as int;
    final suListEnabled = results[5] as bool;
    final customColors = results[6] as Map<int, int>;
    
    // Apply overrides dynamically after initialization
    // We update providers
    ref.read(themeProvider.notifier).state = isDarkMode;
    ref.read(tileColorProvider.notifier).state = tileColor;
    ref.read(suListEnabledProvider.notifier).state = suListEnabled;
    
    // Load custom tile colors into AppTheme and provider
    if (customColors.isNotEmpty) {
      final colorMap = customColors.map((key, value) => MapEntry(key, Color(value)));
      AppTheme.customTileColors = colorMap;
      ref.read(customTileColorsProvider.notifier).state = colorMap;
    }
    
    // Initialize AndroidDataService asynchronously
    AndroidDataService.initialize();

    if (mounted) {
      setState(() {
        _initialized = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_initialized) {
      // Show an immediate minimal splash screen or empty container to unblock first frame
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        home: Scaffold(backgroundColor: Colors.black),
      );
    }

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

    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        // Update Monet static properties with full Material You color palette
        // Use lightDynamic for consistent tile colors regardless of app theme
        AppTheme.monetPrimary = lightDynamic?.primary;
        AppTheme.monetSecondary = lightDynamic?.secondary;
        AppTheme.monetTertiary = lightDynamic?.tertiary;
        AppTheme.monetSurface = lightDynamic?.surface;
        AppTheme.monetError = lightDynamic?.error;
        AppTheme.monetPrimaryContainer = lightDynamic?.primaryContainer;
        AppTheme.monetSecondaryContainer = lightDynamic?.secondaryContainer;
        AppTheme.monetTertiaryContainer = lightDynamic?.tertiaryContainer;
        
        // Update Monet providers asynchronously to avoid state update during build
        WidgetsBinding.instance.addPostFrameCallback((_) {
          final primary = lightDynamic?.primary;
          final secondary = lightDynamic?.secondary;
          final tertiary = lightDynamic?.tertiary;
          final error = lightDynamic?.error;
          
          if (ref.read(monetPrimaryProvider) != primary) {
            ref.read(monetPrimaryProvider.notifier).state = primary;
          }
          if (ref.read(monetSecondaryProvider) != secondary) {
            ref.read(monetSecondaryProvider.notifier).state = secondary;
          }
          if (ref.read(monetTertiaryProvider) != tertiary) {
            ref.read(monetTertiaryProvider.notifier).state = tertiary;
          }
          if (ref.read(monetErrorProvider) != error) {
            ref.read(monetErrorProvider.notifier).state = error;
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
            fontFamily: 'SourceHanSans, Poppins',
          ),
          home: const AnimatedDashboardScreen(),
        );
      },
    );
  }

  TextTheme _buildTextTheme() {
    return GoogleFonts.poppinsTextTheme().apply(
      bodyColor: const Color(0xFF000000),
      displayColor: const Color(0xFF000000),
    );
  }
}
