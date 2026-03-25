import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'providers/dashboard_providers.dart';
import 'navigation/flip_page_route.dart';
import 'screens/secondary_pages.dart';
import 'l10n/app_localizations.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});

  @override
  ConsumerState<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends ConsumerState<DashboardScreen> {
  Timer? _carouselTimer;
  int _modulesCarouselIndex = 0;
  int _appsCarouselIndex = 0;

  @override
  void initState() {
    super.initState();
    _startCarouselTimer();
  }

  @override
  void dispose() {
    _carouselTimer?.cancel();
    super.dispose();
  }

  void _startCarouselTimer() {
    _carouselTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final modules = ref.read(modulesProvider);
      final apps = ref.read(appsProvider);
      
      // Filter enabled modules
      final enabledModules = modules.where((m) => m.isEnabled).toList();
      final enabledApps = apps.where((a) => a.hasRootAccess).toList();
      
      setState(() {
        // Update modules carousel index
        if (enabledModules.length > 3) {
          _modulesCarouselIndex = (_modulesCarouselIndex + 1) % enabledModules.length;
        } else {
          _modulesCarouselIndex = 0;
        }
        
        // Update apps carousel index
        if (enabledApps.length > 3) {
          _appsCarouselIndex = (_appsCarouselIndex + 1) % enabledApps.length;
        } else {
          _appsCarouselIndex = 0;
        }
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);

    return TickerMode(
      enabled: ModalRoute.of(context)?.isCurrent ?? true, // ← 这里改了: 当页面被推入后台或遮挡时暂停动画 Ticker
      child: Scaffold(
        backgroundColor: AppTheme.getBackground(isDark),
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(4.0),
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
      case '/settings':
        page = const SettingsPage();
        break;
      case '/modules':
        page = const ModulesPage();
        break;
      case '/apps':
        page = const AppsPage();
        break;
      case '/denylist':
        page = const DenyListPage();
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
        Expanded(flex: 3, child: _buildSettingsCard(context, ref, isDark)),
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

  Color _getTileColor(int tileIndex, int tileColorIndex, bool isDark, Map<int, Color> customColors) {
    // tileIndex: 0=Magisk, 1=Settings, 2=Contributor, 3=Modules, 4=Apps
    if (tileColorIndex == 0) {
      // Default theme
      switch (tileIndex) {
        case 0: return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
        case 1: return isDark ? const Color(0xFFFFC107) : const Color(0xFFFFD54F);
        case 2: return isDark ? const Color(0xFF9C27B0) : const Color(0xFFBA68C8);
        case 3: return isDark ? const Color(0xFF4285F4) : const Color(0xFF64B5F6);
        case 4: return isDark ? const Color(0xFFD32F2F) : const Color(0xFFEF5350);
        default: return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
      }
    } else if (tileColorIndex == 1) {
      // Monet theme
      final monetColor = AppTheme.monetPrimary ?? const Color(0xFF009688);
      return isDark ? monetColor : monetColor.withValues(alpha: 0.7);
    } else if (tileColorIndex == 2) {
      // Custom theme - use per-tile custom colors directly (user's choice)
      // Use the passed customColors parameter for reactivity
      final customColor = customColors[tileIndex] ?? AppTheme.customTileColors[tileIndex];
      if (customColor != null) {
        return customColor; // Use the exact color user selected
      }
      // Fallback to default if custom color not set
      switch (tileIndex) {
        case 0: return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
        case 1: return isDark ? const Color(0xFFFFC107) : const Color(0xFFFFD54F);
        case 2: return isDark ? const Color(0xFF9C27B0) : const Color(0xFFBA68C8);
        case 3: return isDark ? const Color(0xFF4285F4) : const Color(0xFF64B5F6);
        case 4: return isDark ? const Color(0xFFD32F2F) : const Color(0xFFEF5350);
        default: return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
      }
    } else {
      // Other preset colors (Blue, Red, Green, Purple, Yellow)
      final baseColor = AppTheme.tileColors[tileColorIndex - 3];
      return isDark ? baseColor : baseColor.withValues(alpha: 0.7);
    }
  }

  Widget _buildMagiskCard(BuildContext context, WidgetRef ref, bool isDark) {
    final status = ref.watch(magiskStatusProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = _getTileColor(0, tileColorIndex, isDark, customColors);

    // Magisk card is always clickable
    return GestureDetector(
      onTap: () => _navigateTo(context, '/magisk'),
      child: Container(
        color: tileColor,
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

  Widget _buildSettingsCard(BuildContext context, WidgetRef ref, bool isDark) {
    final status = ref.watch(magiskStatusProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = _getTileColor(1, tileColorIndex, isDark, customColors);

    // Only clickable if rooted with Magisk
    final isClickable = status.isRooted;

    return Opacity(
      opacity: isClickable ? 1.0 : 0.5,
      child: GestureDetector(
        onTap: isClickable ? () => _navigateTo(context, '/settings') : null,
        child: Container(
          color: tileColor,
          child: Padding(
            padding: const EdgeInsets.all(12.0),
            child: Align(
              alignment: Alignment.topRight,
              child: Text(
                isClickable 
                    ? localizations.settings
                    : '${localizations.settings} [N/A]',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 20,
                  color: Colors.black,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContributorCard(
      BuildContext context, WidgetRef ref, bool isDark) {
    final status = ref.watch(magiskStatusProvider);
    final contributors = ref.watch(contributorsProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = _getTileColor(2, tileColorIndex, isDark, customColors);

    // Only clickable if rooted with Magisk
    final isClickable = status.isRooted;

    return Opacity(
      opacity: isClickable ? 1.0 : 0.5,
      child: GestureDetector(
        onTap: isClickable ? () => _navigateTo(context, '/contributors') : null,
        child: Container(
          color: tileColor,
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
      ),
    );
  }

  Widget _buildModulesCard(BuildContext context, WidgetRef ref, bool isDark) {
    final status = ref.watch(magiskStatusProvider);
    final modules = ref.watch(modulesProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = _getTileColor(3, tileColorIndex, isDark, customColors);

    // Only clickable if rooted with MagiskSU (not other root solutions)
    final isClickable = status.isRooted;

    // Filter enabled modules for carousel
    final enabledModules = modules.where((m) => m.isEnabled).toList();

    return Opacity(
      opacity: isClickable ? 1.0 : 0.5,
      child: GestureDetector(
        onTap: isClickable ? () => _navigateTo(context, '/modules') : null,
        child: Container(
          color: tileColor,
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
                // Display up to 3 modules with carousel effect using ListWheelAnimator
                _CarouselItemDisplay(
                  key: const ValueKey('modules_carousel'),
                  items: enabledModules.map((m) => m.name).toList(),
                  startIndex: _modulesCarouselIndex,
                  maxItems: 3,
                ),
                const Spacer(),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Text(
                      '${enabledModules.length}',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: 32,
                        color: Colors.black,
                      ),
                    ),
                    if (enabledModules.length > 3)
                      Padding(
                        padding: const EdgeInsets.only(left: 4, bottom: 8),
                        child: Icon(
                          Icons.arrow_forward,
                          size: 16,
                          color: Colors.black.withValues(alpha: 0.6),
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAppsCard(BuildContext context, WidgetRef ref, bool isDark) {
    final status = ref.watch(magiskStatusProvider);
    final apps = ref.watch(appsProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = _getTileColor(4, tileColorIndex, isDark, customColors);

    // Only clickable if rooted with Magisk
    final isClickable = status.isRooted;

    // Filter apps with root access for carousel
    final rootApps = apps.where((a) => a.hasRootAccess).toList();

    return Opacity(
      opacity: isClickable ? 1.0 : 0.5,
      child: GestureDetector(
        onTap: isClickable ? () => _navigateTo(context, '/apps') : null,
        child: Container(
          color: tileColor,
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
                // Display up to 3 apps with carousel effect
                _CarouselItemDisplay(
                  key: const ValueKey('apps_carousel'),
                  items: rootApps.map((a) => a.name).toList(),
                  startIndex: _appsCarouselIndex,
                  maxItems: 3,
                ),
                const Spacer(),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Text(
                      '${rootApps.length}',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: 32,
                        color: Colors.black,
                      ),
                    ),
                    if (rootApps.length > 3)
                      Padding(
                        padding: const EdgeInsets.only(left: 4, bottom: 8),
                        child: Icon(
                          Icons.arrow_forward,
                          size: 16,
                          color: Colors.black.withValues(alpha: 0.6),
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Get 3 items from a list starting from the carousel index
  /// Returns items with their global index for proper animation keys
  List<(T, int)> _getCarouselItemsWithIndex<T>(List<T> items, int startIndex) {
    if (items.isEmpty) return [];
    if (items.length <= 3) {
      return items.asMap().entries.map((e) => (e.value, e.key)).toList();
    }
    
    final result = <(T, int)>[];
    for (int i = 0; i < 3; i++) {
      final index = (startIndex + i) % items.length;
      result.add((items[index], index));
    }
    return result;
  }

  Widget _buildLogsCard(BuildContext context, WidgetRef ref, bool isDark) {
    final filteredLogs = ref.watch(filteredLogsProvider);
    final localizations = AppLocalizations.of(context)!;

    // Logs card is always clickable
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
                child: filteredLogs.isEmpty
                    ? Center(
                        child: Text(
                          'No E/W/D logs',
                          style: GoogleFonts.poppins(
                            fontWeight: FontWeight.w500,
                            fontSize: 12,
                            color: isDark ? Colors.black : Colors.white,
                          ),
                        ),
                      )
                    : _LogsListView(logs: filteredLogs, isDark: isDark),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// A widget that displays carousel items with proper animation
/// Each item animates independently when the list changes
class _CarouselItemDisplay extends StatelessWidget {
  final List<String> items;
  final int startIndex;
  final int maxItems;

  const _CarouselItemDisplay({
    super.key,
    required this.items,
    required this.startIndex,
    this.maxItems = 3,
  });

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const SizedBox.shrink();
    }
    
    // Get the items to display
    final displayCount = items.length > maxItems ? maxItems : items.length;
    final displayItems = <String>[];
    
    for (int i = 0; i < displayCount; i++) {
      final index = (startIndex + i) % items.length;
      displayItems.add(items[index]);
    }
    
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: displayItems.asMap().entries.map((entry) {
        final position = entry.key;
        final item = entry.value;
        // Use position + startIndex to create unique key that changes with carousel
        final uniqueKey = '${item}_${startIndex}_${position}';
        return AnimatedSwitcher(
          duration: const Duration(milliseconds: 300),
          transitionBuilder: (Widget child, Animation<double> animation) {
            return SlideTransition(
              position: Tween<Offset>(
                begin: const Offset(0.3, 0),
                end: Offset.zero,
              ).animate(CurvedAnimation(
                parent: animation,
                curve: Curves.fastOutSlowIn, // ← 这里改了: 使用统一的平滑曲线
              )),
              child: FadeTransition(opacity: animation, child: child),
            );
          },
          child: Text(
            item,
            key: ValueKey(uniqueKey),
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w500,
              fontSize: 14,
              color: Colors.black,
            ),
          ),
        );
      }).toList(),
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
    // Logs are already filtered (E/W/D for dashboard tile), just display them
    return ListView.builder(
      controller: _scrollController,
      itemCount: widget.logs.length,
      itemBuilder: (context, index) {
        final log = widget.logs[index];
        return RepaintBoundary( // ← 这里改了: 隔离高频重绘列表项，防止整个列表甚至页面被污染重绘
          child: Text(
            log,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w500,
              fontSize: 8,
              color: widget.isDark ? Colors.black : Colors.white,
            ),
            overflow: TextOverflow.clip,
            maxLines: 1,
          ),
        );
      },
    );
  }
}
