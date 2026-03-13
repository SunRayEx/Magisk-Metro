import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'providers/dashboard_providers.dart';
import 'navigation/flip_page_route.dart';
import 'screens/secondary_pages.dart';
import 'l10n/app_localizations.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              _buildTopBar(context, ref, isDark),
              Expanded(
                child: _buildMainContent(context, ref, isDark),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context, WidgetRef ref, bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          GestureDetector(
            onTap: () => _showSettings(context, ref),
            child: Container(
              padding: const EdgeInsets.all(8),
              child: Icon(
                Icons.settings,
                color: AppTheme.getFont(isDark),
                size: 24,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showSettings(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => SettingsSheet(
        onThemeTap: () {
          Navigator.pop(context);
          _navigateTo(context, '/theme');
        },
      ),
    );
  }

  void _navigateTo(BuildContext context, String route) {
    Widget page;
    switch (route) {
      case '/magisk':
        page = const MagiskManagerPage();
        break;
      case '/denylist':
        page = const DenyListPage();
        break;
      case '/modules':
        page = const ModulesPage();
        break;
      case '/apps':
        page = const AppsPage();
        break;
      case '/logs':
        page = const LogsPage();
        break;
      case '/contributors':
        page = const ContributorsPage();
        break;
      case '/theme':
        page = const ThemePage();
        break;
      default:
        return;
    }
    Navigator.push(context, FlipPageRoute(page: page));
  }

  Widget _buildMainContent(BuildContext context, WidgetRef ref, bool isDark) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: _buildLeftColumn(context, ref, isDark),
        ),
        const SizedBox(width: 4),
        Expanded(
          child: _buildRightColumn(context, ref, isDark),
        ),
      ],
    );
  }

  Widget _buildLeftColumn(BuildContext context, WidgetRef ref, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(flex: 6, child: _buildMagiskCard(context, ref, isDark)),
        const SizedBox(height: 4),
        Expanded(flex: 3, child: _buildDenyListCard(context, ref, isDark)),
        const SizedBox(height: 4),
        Expanded(flex: 3, child: _buildContributorCard(context, ref, isDark)),
      ],
    );
  }

  Widget _buildRightColumn(BuildContext context, WidgetRef ref, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(flex: 3, child: _buildModulesCard(context, ref, isDark)),
        const SizedBox(height: 4),
        Expanded(flex: 3, child: _buildAppsCard(context, ref, isDark)),
        const SizedBox(height: 4),
        Expanded(flex: 6, child: _buildLogsCard(context, ref, isDark)),
      ],
    );
  }

  Widget _buildMagiskCard(BuildContext context, WidgetRef ref, bool isDark) {
    final status = ref.watch(magiskStatusProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFF4DB6AC)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/magisk'),
      child: Container(
        color: isDark ? tileColor : tileColorLight,
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Column(
            children: [
              Icon(
                Icons.face,
                size: 60,
                color: isDark ? Colors.black : Colors.white,
              ),
              const SizedBox(height: 8),
              Text(
                'Magisk ${status.versionCode}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 16,
                  color: Colors.black,
                ),
              ),
              Text(
                '[${status.isRooted ? localizations.enabled : localizations.disabled}]',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w600,
                  fontSize: 12,
                  color: Colors.black,
                ),
              ),
              const Spacer(),
              _buildStatusRow(localizations.root, status.isRooted, localizations),
              _buildStatusRow(localizations.zygisk, status.isZygiskEnabled, localizations),
              _buildStatusRow(localizations.ramdisk, status.isRamdiskLoaded, localizations),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusRow(String label, bool value, AppLocalizations localizations) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            '$label:',
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w600,
              fontSize: 10,
              color: Colors.black,
            ),
          ),
          Text(
            value ? localizations.yes : localizations.no,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: 10,
              color: Colors.black,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDenyListCard(BuildContext context, WidgetRef ref, bool isDark) {
    final isEnabled = ref.watch(denyListEnabledProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFFFFC107) : const Color(0xFFFFD54F))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFFFFD54F)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/denylist'),
      child: Container(
        color: isDark ? tileColor : tileColorLight,
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Align(
            alignment: Alignment.topRight,
            child: Text(
              isEnabled ? localizations.denyList : '${localizations.denyList} [OFF]',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 20,
                color: Colors.black,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContributorCard(
      BuildContext context, WidgetRef ref, bool isDark) {
    final contributors = ref.watch(contributorsProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFF9C27B0) : const Color(0xFFBA68C8))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFFBA68C8)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/contributors'),
      child: Container(
        color: isDark ? tileColor : tileColorLight,
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                localizations.contributors,
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 20,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                contributors.isNotEmpty ? contributors.first.name : '',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w500,
                  fontSize: 16,
                  color: Colors.black,
                ),
              ),
              if (contributors.length > 1)
                Text(
                  contributors[1].name,
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w500,
                    fontSize: 16,
                    color: Colors.black,
                  ),
                ),
              const Spacer(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildModulesCard(BuildContext context, WidgetRef ref, bool isDark) {
    final modules = ref.watch(modulesProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFF4285F4) : const Color(0xFF64B5F6))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFF64B5F6)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/modules'),
      child: Container(
        color: isDark ? tileColor : tileColorLight,
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                localizations.modules,
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 20,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                modules.isNotEmpty ? modules.first.name : '',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w500,
                  fontSize: 14,
                  color: Colors.black,
                ),
              ),
              if (modules.length > 1)
                Text(
                  modules[1].name,
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w500,
                    fontSize: 14,
                    color: Colors.black,
                  ),
                ),
              const Spacer(),
              Text(
                '${modules.length}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 32,
                  color: Colors.black,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAppsCard(BuildContext context, WidgetRef ref, bool isDark) {
    final apps = ref.watch(appsProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFFD32F2F) : const Color(0xFFEF5350))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFFEF5350)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/apps'),
      child: Container(
        color: isDark ? tileColor : tileColorLight,
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                localizations.apps,
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 20,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                apps.isNotEmpty ? apps.first.name : '',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w500,
                  fontSize: 14,
                  color: Colors.black,
                ),
              ),
              if (apps.length > 1)
                Text(
                  apps[1].name,
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w500,
                    fontSize: 14,
                    color: Colors.black,
                  ),
                ),
              const Spacer(),
              Text(
                '${apps.length}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 32,
                  color: Colors.black,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLogsCard(BuildContext context, WidgetRef ref, bool isDark) {
    final logsAsync = ref.watch(logsProvider);
    final localizations = AppLocalizations.of(context)!;

    return GestureDetector(
      onTap: () => _navigateTo(context, '/logs'),
      child: Container(
        color: isDark ? Colors.white : Colors.black,
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Column(
            children: [
              Align(
                alignment: Alignment.topRight,
                child: Text(
                  localizations.logs,
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w900,
                    fontSize: 20,
                    color: isDark ? Colors.black : Colors.white,
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Expanded(
                child: logsAsync.when(
                  data: (logs) => _LogsListView(logs: logs, isDark: isDark),
                  loading: () => Center(
                    child: CircularProgressIndicator(
                      color: isDark ? Colors.black : Colors.white,
                    ),
                  ),
                  error: (error, stack) => Text(
                    '[E] ${localizations.error}: $error',
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w500,
                      fontSize: 8,
                      color: isDark ? Colors.black : Colors.white,
                    ),
                    overflow: TextOverflow.clip,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LogsListView extends StatefulWidget {
  final List<String> logs;
  final bool isDark;

  const _LogsListView({required this.logs, required this.isDark});

  @override
  State<_LogsListView> createState() => _LogsListViewState();
}

class _LogsListViewState extends State<_LogsListView> {
  final ScrollController _scrollController = ScrollController();

  @override
  void didUpdateWidget(_LogsListView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.logs.length > oldWidget.logs.length) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            _scrollController.position.maxScrollExtent,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final recentLogs =
        widget.logs.where((log) => log.contains('[E]')).take(10).toList();

    return ListView.builder(
      controller: _scrollController,
      itemCount: recentLogs.isEmpty ? widget.logs.length : recentLogs.length,
      itemBuilder: (context, index) {
        final log = recentLogs.isEmpty ? widget.logs[index] : recentLogs[index];
        return Text(
          log,
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w500,
            fontSize: 8,
            color: widget.isDark ? Colors.black : Colors.white,
          ),
          overflow: TextOverflow.clip,
          maxLines: 1,
        );
      },
    );
  }
}
