import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'providers/dashboard_providers.dart';
import 'navigation/flip_page_route.dart' hide AnimatedBuilder;
import 'screens/secondary_pages.dart';
import 'l10n/app_localizations.dart';

/// Dashboard Screen with rich animations and transitions
class AnimatedDashboardScreen extends ConsumerStatefulWidget {
  const AnimatedDashboardScreen({super.key});

  @override
  ConsumerState<AnimatedDashboardScreen> createState() =>
      _AnimatedDashboardScreenState();
}

class _AnimatedDashboardScreenState
    extends ConsumerState<AnimatedDashboardScreen>
    with AutomaticKeepAliveClientMixin, TickerProviderStateMixin {
  
  // Main entrance animation controller
  late final AnimationController _entranceController;
  
  // Staggered animations for each card
  late final List<AnimationController> _cardControllers;
  late final List<Animation<double>> _cardAnimations;
  
  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    
    // Main entrance controller
    _entranceController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    
    // Initialize 6 card controllers for staggered animation
    _cardControllers = List.generate(6, (index) {
      return AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 400),
      );
    });
    
    // Create staggered animations with delays
    _cardAnimations = _cardControllers.map((controller) {
      return CurvedAnimation(
        parent: controller,
        curve: Curves.easeOutBack,
      );
    }).toList();
    
    // Start entrance animation after first frame
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _entranceController.forward();
        // Stagger card animations
        for (int i = 0; i < _cardControllers.length; i++) {
          Future.delayed(Duration(milliseconds: 100 + (i * 80)), () {
            if (mounted) {
              _cardControllers[i].forward();
            }
          });
        }
      }
    });
  }

  @override
  void dispose() {
    _entranceController.dispose();
    for (final controller in _cardControllers) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              _buildTopBar(context, isDark),
              Expanded(
                child: _buildMainContent(context, isDark, tileColorIndex),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context, bool isDark) {
    // Settings icon removed - settings now accessible via Settings tile
    return const SizedBox.shrink();
  }

  Widget _buildMainContent(BuildContext context, bool isDark, int tileColorIndex) {
    return FadeTransition(
      opacity: _entranceController,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: _buildLeftColumn(context, isDark, tileColorIndex),
          ),
          const SizedBox(width: 4),
          Expanded(
            child: _buildRightColumn(context, isDark, tileColorIndex),
          ),
        ],
      ),
    );
  }

  Widget _buildLeftColumn(BuildContext context, bool isDark, int tileColorIndex) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          flex: 6,
          child: _AnimatedCardEntry(
            animation: _cardAnimations[0],
            child: _MagiskCard(isDark: isDark, tileColorIndex: tileColorIndex),
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          flex: 3,
          child: _AnimatedCardEntry(
            animation: _cardAnimations[1],
            child: _SettingsCard(isDark: isDark, tileColorIndex: tileColorIndex),
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          flex: 3,
          child: _AnimatedCardEntry(
            animation: _cardAnimations[2],
            child: _ContributorCard(isDark: isDark, tileColorIndex: tileColorIndex),
          ),
        ),
      ],
    );
  }

  Widget _buildRightColumn(BuildContext context, bool isDark, int tileColorIndex) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          flex: 3,
          child: _AnimatedCardEntry(
            animation: _cardAnimations[3],
            child: _ModulesCard(isDark: isDark, tileColorIndex: tileColorIndex),
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          flex: 3,
          child: _AnimatedCardEntry(
            animation: _cardAnimations[4],
            child: _AppsCard(isDark: isDark, tileColorIndex: tileColorIndex),
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          flex: 6,
          child: _AnimatedCardEntry(
            animation: _cardAnimations[5],
            child: _LogsCard(isDark: isDark),
          ),
        ),
      ],
    );
  }

  void _showSettings(BuildContext context) {
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
}

/// Optimized Magisk Card with minimal rebuilds
class _MagiskCard extends ConsumerWidget {
  final bool isDark;
  final int tileColorIndex;

  const _MagiskCard({required this.isDark, required this.tileColorIndex});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = AppTheme.getTileWithIndex(0, tileColorIndex, isDark);

    return _AnimatedTileCard(
      color: tileColor,
      onTap: () => _navigateTo(context, ref, '/magisk'),
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
              textAlign: TextAlign.center,
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
            _StatusRow(label: localizations.root, value: status.isRooted),
            _StatusRow(label: localizations.zygisk, value: status.isZygiskEnabled),
            _StatusRow(label: localizations.ramdisk, value: status.isRamdiskLoaded),
          ],
        ),
      ),
    );
  }

  void _navigateTo(BuildContext context, WidgetRef ref, String route) {
    Widget page;
    switch (route) {
      case '/magisk':
        page = const MagiskManagerPage();
        break;
      default:
        return;
    }
    Navigator.push(context, FlipPageRoute(page: page));
  }
}

/// Optimized Settings Card (replaces DenyList Card)
class _SettingsCard extends ConsumerWidget {
  final bool isDark;
  final int tileColorIndex;

  const _SettingsCard({required this.isDark, required this.tileColorIndex});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = AppTheme.getTileWithIndex(1, tileColorIndex, isDark);

    return _AnimatedTileCard(
      color: tileColor,
      onTap: () => Navigator.push(context, FlipPageRoute(page: const SettingsPage())),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Align(
          alignment: Alignment.topRight,
          child: Text(
            localizations.settings,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: 20,
              color: Colors.black,
            ),
          ),
        ),
      ),
    );
  }
}

/// Optimized Contributor Card with scrolling contributor names
class _ContributorCard extends ConsumerWidget {
  final bool isDark;
  final int tileColorIndex;

  const _ContributorCard({required this.isDark, required this.tileColorIndex});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final contributors = ref.watch(contributorsProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = AppTheme.getTileWithIndex(2, tileColorIndex, isDark);
    
    // Get all contributor names for infinite scrolling
    final contributorNames = contributors.map((c) => c.name).toList();

    return _AnimatedTileCard(
      color: tileColor,
      onTap: () => Navigator.push(context, FlipPageRoute(page: const ContributorsPage())),
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
            // Scrolling contributor names - infinite loop, max 3 items
            Expanded(
              child: contributorNames.isNotEmpty
                  ? _ScrollingTextCarousel(
                      items: contributorNames,
                      maxItems: 3,
                      infinite: true,
                      duration: const Duration(seconds: 2),
                      textStyle: GoogleFonts.poppins(
                        fontWeight: FontWeight.w500,
                        fontSize: 12,
                        color: Colors.black,
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }
}

/// Optimized Modules Card with scrolling enabled module names
class _ModulesCard extends ConsumerWidget {
  final bool isDark;
  final int tileColorIndex;

  const _ModulesCard({required this.isDark, required this.tileColorIndex});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final modules = ref.watch(modulesProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = AppTheme.getTileWithIndex(3, tileColorIndex, isDark);
    
    // Get only enabled module names for infinite scrolling
    final enabledModules = modules.where((m) => m.isEnabled).toList();
    final enabledModuleNames = enabledModules.map((m) => m.name).toList();
    final enabledCount = enabledModules.length;

    return _AnimatedTileCard(
      color: tileColor,
      onTap: () => Navigator.push(context, FlipPageRoute(page: const ModulesPage())),
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
            // Scrolling enabled module names - infinite loop, max 3 items
            Expanded(
              child: enabledModuleNames.isNotEmpty
                  ? _ScrollingTextCarousel(
                      items: enabledModuleNames,
                      maxItems: 3,
                      infinite: true,
                      duration: const Duration(seconds: 2),
                      textStyle: GoogleFonts.poppins(
                        fontWeight: FontWeight.w500,
                        fontSize: 12,
                        color: Colors.black,
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
            const SizedBox(height: 4),
            Text(
              '$enabledCount',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 32,
                color: Colors.black,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Optimized Apps Card with scrolling root-granted app names
class _AppsCard extends ConsumerWidget {
  final bool isDark;
  final int tileColorIndex;

  const _AppsCard({required this.isDark, required this.tileColorIndex});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(appsProvider);
    final localizations = AppLocalizations.of(context)!;
    final tileColor = AppTheme.getTileWithIndex(4, tileColorIndex, isDark);
    
    // Get only apps with root access granted for infinite scrolling
    final rootGrantedApps = apps.where((a) => a.hasRootAccess).toList();
    final rootGrantedAppNames = rootGrantedApps.map((a) => a.name).toList();
    final rootGrantedCount = rootGrantedApps.length;

    return _AnimatedTileCard(
      color: tileColor,
      onTap: () => Navigator.push(context, FlipPageRoute(page: const AppsPage())),
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
            // Scrolling root-granted app names - infinite loop, max 3 items
            Expanded(
              child: rootGrantedAppNames.isNotEmpty
                  ? _ScrollingTextCarousel(
                      items: rootGrantedAppNames,
                      maxItems: 3,
                      infinite: true,
                      duration: const Duration(seconds: 2),
                      textStyle: GoogleFonts.poppins(
                        fontWeight: FontWeight.w500,
                        fontSize: 12,
                        color: Colors.black,
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
            const SizedBox(height: 4),
            Text(
              '$rootGrantedCount',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 32,
                color: Colors.black,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Optimized Logs Card with scrolling log entries (E, D, W, I levels)
class _LogsCard extends ConsumerWidget {
  final bool isDark;

  const _LogsCard({required this.isDark});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final logsAsync = ref.watch(logsProvider);
    final localizations = AppLocalizations.of(context)!;

    return _AnimatedTileCard(
      color: isDark ? Colors.white : Colors.black,
      onTap: () => Navigator.push(context, FlipPageRoute(page: const LogsPage())),
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
                data: (logs) {
                  // Filter to show E (Error), D (Debug), W (Warning), I (Info) logs
                  final filteredLogs = logs.where((log) => 
                    log.contains('[E]') || 
                    log.contains('[D]') || 
                    log.contains('[W]') || 
                    log.contains('[I]') ||
                    log.contains('ERROR') || 
                    log.contains('Error') ||
                    log.contains('error') ||
                    log.contains('DEBUG') || 
                    log.contains('Debug') ||
                    log.contains('debug') ||
                    log.contains('WARN') || 
                    log.contains('Warning') ||
                    log.contains('warning') ||
                    log.contains('INFO') || 
                    log.contains('Info') ||
                    log.contains('info')
                  ).take(30).toList();
                  
                  return _ScrollingTextCarousel(
                    items: filteredLogs,
                    maxItems: 30, // Max 30 logs displayed
                    infinite: true, // Loop through logs
                    duration: const Duration(milliseconds: 500), // 0.5s interval
                    textStyle: GoogleFonts.poppins(
                      fontWeight: FontWeight.w500,
                      fontSize: 8,
                      color: isDark ? Colors.black : Colors.white,
                    ),
                  );
                },
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
    );
  }
}

/// Status row widget
class _StatusRow extends StatelessWidget {
  final String label;
  final bool value;

  const _StatusRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
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
            value ? 'Yes' : 'No',
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
}

/// Animated tile card with press feedback (for non-Magisk/DenyList tiles)
class _AnimatedTileCard extends StatefulWidget {
  final Color color;
  final VoidCallback onTap;
  final Widget child;

  const _AnimatedTileCard({
    required this.color,
    required this.onTap,
    required this.child,
  });

  @override
  State<_AnimatedTileCard> createState() => _AnimatedTileCardState();
}

class _AnimatedTileCardState extends State<_AnimatedTileCard> {
  double _scale = 1.0;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _scale = 0.95),
      onTapUp: (_) {
        setState(() => _scale = 1.0);
        widget.onTap();
      },
      onTapCancel: () => setState(() => _scale = 1.0),
      child: AnimatedScale(
        scale: _scale,
        duration: const Duration(milliseconds: 100),
        curve: Curves.easeInOut,
        child: Container(
          color: widget.color,
          child: widget.child,
        ),
      ),
    );
  }
}

/// Animated card entry widget with scale and fade transition
class _AnimatedCardEntry extends StatelessWidget {
  final Animation<double> animation;
  final Widget child;

  const _AnimatedCardEntry({
    required this.animation,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) {
        return Transform.scale(
          scale: 0.8 + (0.2 * animation.value),
          child: Opacity(
            opacity: animation.value,
            child: child,
          ),
        );
      },
      child: child,
    );
  }
}

/// Scrolling text carousel widget - queue-style scrolling with smooth transitions
/// - maxItems: maximum items to display (default 3)
/// - infinite: true for infinite loop, false for stop at end
/// - duration: time between item changes (2 seconds)
class _ScrollingTextCarousel extends StatefulWidget {
  final List<String> items;
  final int maxItems;
  final bool infinite;
  final Duration duration;
  final TextStyle? textStyle;

  const _ScrollingTextCarousel({
    required this.items,
    this.maxItems = 3,
    this.infinite = true,
    this.duration = const Duration(seconds: 2),
    this.textStyle,
  });

  @override
  State<_ScrollingTextCarousel> createState() => _ScrollingTextCarouselState();
}

class _ScrollingTextCarouselState extends State<_ScrollingTextCarousel> {
  List<_CarouselItem> _visibleItems = [];
  int _currentIndex = 0;
  bool _isAnimating = false;

  @override
  void initState() {
    super.initState();
    _startCarousel();
  }

  void _startCarousel() {
    if (widget.items.isEmpty) return;
    
    Future.delayed(widget.duration, () {
      if (!mounted) return;
      _animateNextItem();
    });
  }

  void _animateNextItem() {
    if (!mounted || _isAnimating) return;
    
    // Check if we should continue
    if (!widget.infinite && _currentIndex >= widget.items.length) {
      return;
    }
    
    setState(() {
      _isAnimating = true;
      
      // Mark oldest item for removal (will animate out first)
      if (_visibleItems.length >= widget.maxItems) {
        _visibleItems[0].isRemoving = true;
      }
    });
    
    // Wait for slide-out animation to complete before sliding in new item
    Future.delayed(const Duration(milliseconds: 300), () {
      if (!mounted) return;
      
      setState(() {
        // Remove items marked for removal
        _visibleItems = _visibleItems.where((item) => !item.isRemoving).toList();
        
        // Add new item (will animate in)
        final newItem = _CarouselItem(
          text: widget.items[_currentIndex % widget.items.length],
          isNew: true,
        );
        _visibleItems.add(newItem);
        
        // Update index
        if (widget.infinite) {
          _currentIndex = (_currentIndex + 1) % widget.items.length;
        } else {
          _currentIndex++;
        }
      });
      
      // Wait for slide-in animation to complete
      Future.delayed(const Duration(milliseconds: 300), () {
        if (!mounted) return;
        
        setState(() {
          // Reset new flags
          for (var item in _visibleItems) {
            item.isNew = false;
          }
          _isAnimating = false;
        });
        
        // Continue carousel
        _startCarousel();
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    if (widget.items.isEmpty) {
      return const SizedBox.shrink();
    }
    
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: _visibleItems.map((item) {
        return _AnimatedCarouselItem(
          key: ValueKey(item.text + _visibleItems.indexOf(item).toString()),
          text: item.text,
          isNew: item.isNew,
          isRemoving: item.isRemoving,
          textStyle: widget.textStyle,
        );
      }).toList(),
    );
  }
}

/// Internal class to track item state
class _CarouselItem {
  final String text;
  bool isNew;
  bool isRemoving;

  _CarouselItem({
    required this.text,
    this.isNew = false,
    this.isRemoving = false,
  });
}

/// Animated item widget using pure implicit animations for flicker-free transitions
class _AnimatedCarouselItem extends StatefulWidget {
  final String text;
  final bool isNew;
  final bool isRemoving;
  final TextStyle? textStyle;

  const _AnimatedCarouselItem({
    super.key,
    required this.text,
    this.isNew = false,
    this.isRemoving = false,
    this.textStyle,
  });

  @override
  State<_AnimatedCarouselItem> createState() => _AnimatedCarouselItemState();
}

class _AnimatedCarouselItemState extends State<_AnimatedCarouselItem> {
  double _opacity = 1.0;
  double _slideY = 0.0;

  @override
  void initState() {
    super.initState();
    if (widget.isNew) {
      // Start invisible and below for new items
      _opacity = 0.0;
      _slideY = 15.0;
      // Schedule animation for next frame
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          setState(() {
            _opacity = 1.0;
            _slideY = 0.0;
          });
        }
      });
    }
  }

  @override
  void didUpdateWidget(_AnimatedCarouselItem oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isRemoving && !oldWidget.isRemoving) {
      // Animate out - slide up and fade
      setState(() {
        _opacity = 0.0;
        _slideY = -15.0;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedOpacity(
      opacity: _opacity,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOutCubic,
      child: AnimatedSlide(
        offset: Offset(0, _slideY / 100), // Convert to relative offset
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOutCubic,
        child: Padding(
          padding: const EdgeInsets.only(bottom: 2),
          child: Text(
            widget.text,
            style: widget.textStyle,
            overflow: TextOverflow.ellipsis,
            maxLines: 1,
          ),
        ),
      ),
    );
  }
}

/// Base tile card without animation (for Magisk and DenyList tiles)
class _TileCard extends StatelessWidget {
  final Color color;
  final VoidCallback onTap;
  final Widget child;

  const _TileCard({
    required this.color,
    required this.onTap,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        color: color,
        child: child,
      ),
    );
  }
}
