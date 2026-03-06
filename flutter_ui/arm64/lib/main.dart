import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'dashboard_screen.dart';

void main() {
  runApp(const ProviderScope(child: MagiskDashboardApp()));
}

class MagiskDashboardApp extends StatelessWidget {
  const MagiskDashboardApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Magisk Dashboard',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFF000000),
        colorScheme: const ColorScheme.light(
          primary: Color(0xFF000000),
          onPrimary: Color(0xFF000000),
        ),
        textTheme: GoogleFonts.poppinsTextTheme().apply(
          bodyColor: const Color(0xFF000000),
          displayColor: const Color(0xFF000000),
        ),
      ),
      home: const DashboardScreen(),
    );
  }
}
