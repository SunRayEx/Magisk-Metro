import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'providers/dashboard_providers.dart';
import 'navigation/flip_page_route.dart' hide AnimatedBuilder;
import 'screens/secondary_pages.dart';

class AnimatedDashboardScreen extends ConsumerStatefulWidget {
  const AnimatedDashboardScreen({super.key});

  @override
  ConsumerState<AnimatedDashboardScreen> createState() =>
      _AnimatedDashboardScreenState();
}

class _AnimatedDashboardScreenState
    extends ConsumerState<AnimatedDashboardScreen>
    with TickerProviderStateMixin {
  late AnimationController _staggerController;
  late AnimationController _gearController;
  final Map<String, AnimationController> _cardControllers = {};

  // ANIMATION: Staggered entrance delays for each card
  static const List<Duration> _cardDelays = [
    Duration(milliseconds: 0),
    Duration(milliseconds: 100),
    Duration(milliseconds: 200),
    Duration(milliseconds: 300),
    Duration(milliseconds: 400),
    Duration(milliseconds: 500),
  ];

  @override
  void initState() {
    super.initState();
    _staggerController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _gearController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    );
    _startStaggeredAnimation();
  }

  void _startStaggeredAnimation() async {
    await Future.delayed(const Duration(milliseconds: 100));
    if (mounted) {
      _staggerController.forward();
    }
  }

  AnimationController _getCardController(String cardId) {
    if (!_cardControllers.containsKey(cardId)) {
      _cardControllers[cardId] = AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 150),
      );
    }
    return _cardControllers[cardId]!;
  }

  @override
  void dispose() {
    _staggerController.dispose();
    _gearController.dispose();
    for (var controller in _cardControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              _buildAnimatedTopBar(context, ref, isDark),
              Expanded(
                child: _buildAnimatedMainContent(context, ref, isDark),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedTopBar(
      BuildContext context, WidgetRef ref, bool isDark) {
    // ANIMATION: SlideTransition + FadeTransition for staggered entrance
    final slideAnimation = Tween<Offset>(
      begin: const Offset(0, -0.5),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _staggerController,
      curve: Curves.fastOutSlowIn,
    ));

    final fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
        parent: _staggerController,
        curve: Curves.fastOutSlowIn,
      ),
    );

    return SlideTransition(
      position: slideAnimation,
      child: FadeTransition(
        opacity: fadeAnimation,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              GestureDetector(
                onTap: () {
                  // ANIMATION: RotationTransition on tap (360° in 400ms)
                  _gearController.forward(from: 0);
                  _showSettings(context, ref);
                },
                child: RotationTransition(
                  turns: Tween<double>(begin: 0, end: 1).animate(
                    CurvedAnimation(
                      parent: _gearController,
                      curve: Curves.easeInOut,
                    ),
                  ),
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    child: Icon(
                      Icons.settings,
                      color: AppTheme.getFont(isDark),
                      size: 24,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedMainContent(
      BuildContext context, WidgetRef ref, bool isDark) {
    // ANIMATION: Staggered entrance using SlideTransition
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: _buildAnimatedLeftColumn(context, ref, isDark),
        ),
        const SizedBox(width: 4),
        Expanded(
          child: _buildAnimatedRightColumn(context, ref, isDark),
        ),
      ],
    );
  }

  Widget _buildAnimatedLeftColumn(
      BuildContext context, WidgetRef ref, bool isDark) {
    return AnimatedBuilder(
      animation: _staggerController,
      builder: (context, child) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              flex: 6,
              child: _buildAnimatedMagiskCard(context, ref, isDark, 0),
            ),
            const SizedBox(height: 4),
            Expanded(
              flex: 3,
              child: _buildAnimatedDenyListCard(context, ref, isDark, 1),
            ),
            const SizedBox(height: 4),
            Expanded(
              flex: 3,
              child: _buildAnimatedContributorCard(context, ref, isDark, 2),
            ),
          ],
        );
      },
    );
  }

  Widget _buildAnimatedRightColumn(
      BuildContext context, WidgetRef ref, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          flex: 4,
          child: _buildAnimatedModulesCard(context, ref, isDark, 3),
        ),
        const SizedBox(height: 4),
        Expanded(
          flex: 4,
          child: _buildAnimatedAppsCard(context, ref, isDark, 4),
        ),
        const SizedBox(height: 4),
        Expanded(
          flex: 4,
          child: _buildAnimatedLogsCard(context, ref, isDark, 5),
        ),
      ],
    );
  }

  // ANIMATION: ScaleTransition + spring physics on card tap
  Widget _buildAnimatedCard({
    required Widget child,
    required int index,
    required VoidCallback onTap,
  }) {
    final controller = _getCardController('card_$index');

    return GestureDetector(
      onTapDown: (_) => controller.forward(),
      onTapUp: (_) {
        controller.reverse();
        onTap();
      },
      onTapCancel: () => controller.reverse(),
      child: ScaleTransition(
        scale: Tween<double>(begin: 1.0, end: 0.95).animate(
          CurvedAnimation(
            parent: controller,
            curve: Curves.elasticOut,
          ),
        ),
        child: child,
      ),
    );
  }

  Widget _buildAnimatedMagiskCard(
      BuildContext context, WidgetRef ref, bool isDark, int index) {
    final status = ref.watch(magiskStatusProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFF4DB6AC)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    // ANIMATION: Staggered slide + fade
    final delay = _cardDelays[index];
    final slideAnimation = Tween<Offset>(
      begin: const Offset(-0.5, 0),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _staggerController,
      curve: Interval(
        delay.inMilliseconds / 1200,
        (delay.inMilliseconds + 400) / 1200,
        curve: Curves.fastOutSlowIn,
      ),
    ));

    final fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
        parent: _staggerController,
        curve: Interval(
          delay.inMilliseconds / 1200,
          (delay.inMilliseconds + 400) / 1200,
          curve: Curves.fastOutSlowIn,
        ),
      ),
    );

    return SlideTransition(
      position: slideAnimation,
      child: FadeTransition(
        opacity: fadeAnimation,
        child: _buildAnimatedCard(
          index: index,
          onTap: () => _navigateTo(context, '/magisk'),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.fastOutSlowIn,
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
                  Center(
                    child: Text(
                      'Magisk ${status.versionCode}',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: 16,
                        color: Colors.black,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                  Center(
                    child: Text(
                      '[${status.isRooted ? "enable" : "disable"}]',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w600,
                        fontSize: 12,
                        color: Colors.black,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Spacer(),
                  _buildAnimatedStatusRow('Root', status.isRooted),
                  _buildAnimatedStatusRow('Zygisk', status.isZygiskEnabled),
                  _buildAnimatedStatusRow('Ramdisk', status.isRamdiskLoaded),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedStatusRow(String label, bool value) {
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
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            child: Text(
              value ? 'Yes' : 'No',
              key: ValueKey(value),
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 10,
                color: Colors.black,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAnimatedDenyListCard(
      BuildContext context, WidgetRef ref, bool isDark, int index) {
    final isEnabled = ref.watch(denyListEnabledProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFFFFC107) : const Color(0xFFFFD54F))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFFFFD54F)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    final delay = _cardDelays[index];
    final slideAnimation = Tween<Offset>(
      begin: const Offset(-0.5, 0),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _staggerController,
      curve: Interval(
        delay.inMilliseconds / 1200,
        (delay.inMilliseconds + 400) / 1200,
        curve: Curves.fastOutSlowIn,
      ),
    ));

    final fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
        parent: _staggerController,
        curve: Interval(
          delay.inMilliseconds / 1200,
          (delay.inMilliseconds + 400) / 1200,
          curve: Curves.fastOutSlowIn,
        ),
      ),
    );

    return SlideTransition(
      position: slideAnimation,
      child: FadeTransition(
        opacity: fadeAnimation,
        child: _buildAnimatedCard(
          index: index,
          onTap: () => _navigateTo(context, '/denylist'),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.fastOutSlowIn,
            color: isDark ? tileColor : tileColorLight,
            child: Padding(
              padding: const EdgeInsets.all(12.0),
              child: Align(
                alignment: Alignment.topRight,
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 200),
                  child: Text(
                    isEnabled ? 'DenyList' : 'DenyList [OFF]',
                    key: ValueKey(isEnabled),
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
        ),
      ),
    );
  }

  Widget _buildAnimatedContributorCard(
      BuildContext context, WidgetRef ref, bool isDark, int index) {
    final contributors = ref.watch(contributorsProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFF9C27B0) : const Color(0xFFBA68C8))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFFBA68C8)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    final delay = _cardDelays[index];
    final slideAnimation = Tween<Offset>(
      begin: const Offset(-0.5, 0),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _staggerController,
      curve: Interval(
        delay.inMilliseconds / 1200,
        (delay.inMilliseconds + 400) / 1200,
        curve: Curves.fastOutSlowIn,
      ),
    ));

    final fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
        parent: _staggerController,
        curve: Interval(
          delay.inMilliseconds / 1200,
          (delay.inMilliseconds + 400) / 1200,
          curve: Curves.fastOutSlowIn,
        ),
      ),
    );

    return SlideTransition(
      position: slideAnimation,
      child: FadeTransition(
        opacity: fadeAnimation,
        child: _buildAnimatedCard(
          index: index,
          onTap: () => _navigateTo(context, '/contributors'),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.fastOutSlowIn,
            color: isDark ? tileColor : tileColorLight,
            child: Padding(
              padding: const EdgeInsets.all(12.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    'Contributor',
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 20,
                      color: Colors.black,
                    ),
                  ),
                  const SizedBox(height: 4),
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 200),
                    child: Text(
                      contributors.isNotEmpty ? contributors.first.name : '',
                      key: ValueKey(contributors.isNotEmpty),
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w500,
                        fontSize: 16,
                        color: Colors.black,
                      ),
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
      ),
    );
  }

  Widget _buildAnimatedModulesCard(
      BuildContext context, WidgetRef ref, bool isDark, int index) {
    final modules = ref.watch(modulesProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFF4285F4) : const Color(0xFF64B5F6))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFF64B5F6)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    final delay = _cardDelays[index];
    final slideAnimation = Tween<Offset>(
      begin: const Offset(0.5, 0),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _staggerController,
      curve: Interval(
        delay.inMilliseconds / 1200,
        (delay.inMilliseconds + 400) / 1200,
        curve: Curves.fastOutSlowIn,
      ),
    ));

    final fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
        parent: _staggerController,
        curve: Interval(
          delay.inMilliseconds / 1200,
          (delay.inMilliseconds + 400) / 1200,
          curve: Curves.fastOutSlowIn,
        ),
      ),
    );

    return SlideTransition(
      position: slideAnimation,
      child: FadeTransition(
        opacity: fadeAnimation,
        child: _buildAnimatedCard(
          index: index,
          onTap: () => _navigateTo(context, '/modules'),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.fastOutSlowIn,
            color: isDark ? tileColor : tileColorLight,
            child: Padding(
              padding: const EdgeInsets.all(12.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    'Modules',
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
                  // ANIMATION: TweenAnimationBuilder<int> for number with 1200ms, easeOutCubic
                  TweenAnimationBuilder<int>(
                    tween: IntTween(begin: 0, end: modules.length),
                    duration: const Duration(milliseconds: 1200),
                    curve: Curves.easeOutCubic,
                    builder: (context, value, child) {
                      return Text(
                        '$value',
                        style: GoogleFonts.poppins(
                          fontWeight: FontWeight.w900,
                          fontSize: 32,
                          color: Colors.black,
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedAppsCard(
      BuildContext context, WidgetRef ref, bool isDark, int index) {
    final apps = ref.watch(appsProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final tileColor = tileColorIndex == 0
        ? (isDark ? const Color(0xFFD32F2F) : const Color(0xFFEF5350))
        : AppTheme.tileColors[tileColorIndex];
    final tileColorLight = tileColorIndex == 0
        ? const Color(0xFFEF5350)
        : AppTheme.tileColors[tileColorIndex].withValues(alpha: 0.7);

    final delay = _cardDelays[index];
    final slideAnimation = Tween<Offset>(
      begin: const Offset(0.5, 0),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _staggerController,
      curve: Interval(
        delay.inMilliseconds / 1200,
        (delay.inMilliseconds + 400) / 1200,
        curve: Curves.fastOutSlowIn,
      ),
    ));

    final fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
        parent: _staggerController,
        curve: Interval(
          delay.inMilliseconds / 1200,
          (delay.inMilliseconds + 400) / 1200,
          curve: Curves.fastOutSlowIn,
        ),
      ),
    );

    return SlideTransition(
      position: slideAnimation,
      child: FadeTransition(
        opacity: fadeAnimation,
        child: _buildAnimatedCard(
          index: index,
          onTap: () => _navigateTo(context, '/apps'),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.fastOutSlowIn,
            color: isDark ? tileColor : tileColorLight,
            child: Padding(
              padding: const EdgeInsets.all(12.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    'Apps',
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
                  // ANIMATION: TweenAnimationBuilder<int> for number with 1200ms, easeOutCubic
                  TweenAnimationBuilder<int>(
                    tween: IntTween(begin: 0, end: apps.length),
                    duration: const Duration(milliseconds: 1200),
                    curve: Curves.easeOutCubic,
                    builder: (context, value, child) {
                      return Text(
                        '$value',
                        style: GoogleFonts.poppins(
                          fontWeight: FontWeight.w900,
                          fontSize: 32,
                          color: Colors.black,
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedLogsCard(
      BuildContext context, WidgetRef ref, bool isDark, int index) {
    final logsAsync = ref.watch(logsProvider);

    final delay = _cardDelays[index];
    final slideAnimation = Tween<Offset>(
      begin: const Offset(0.5, 0),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _staggerController,
      curve: Interval(
        delay.inMilliseconds / 1200,
        (delay.inMilliseconds + 400) / 1200,
        curve: Curves.fastOutSlowIn,
      ),
    ));

    final fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(
        parent: _staggerController,
        curve: Interval(
          delay.inMilliseconds / 1200,
          (delay.inMilliseconds + 400) / 1200,
          curve: Curves.fastOutSlowIn,
        ),
      ),
    );

    return SlideTransition(
      position: slideAnimation,
      child: FadeTransition(
        opacity: fadeAnimation,
        child: _buildAnimatedCard(
          index: index,
          onTap: () => _navigateTo(context, '/logs'),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.fastOutSlowIn,
            color: isDark ? Colors.white : Colors.black,
            child: Padding(
              padding: const EdgeInsets.all(12.0),
              child: Column(
                children: [
                  Align(
                    alignment: Alignment.topRight,
                    child: Text(
                      'Logs',
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
                      data: (logs) =>
                          AnimatedLogsListView(logs: logs, isDark: isDark),
                      loading: () => Center(
                        child: CircularProgressIndicator(
                          color: isDark ? Colors.black : Colors.white,
                        ),
                      ),
                      error: (error, stack) => Text(
                        '[E] Error: $error',
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
        ),
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
}

// ANIMATION: AnimatedList with insert animation (FadeTransition + SizeTransition)
class AnimatedLogsListView extends StatefulWidget {
  final List<String> logs;
  final bool isDark;

  const AnimatedLogsListView({required this.logs, required this.isDark});

  @override
  State<AnimatedLogsListView> createState() => _AnimatedLogsListViewState();
}

class _AnimatedLogsListViewState extends State<AnimatedLogsListView>
    with AutomaticKeepAliveClientMixin {
  final GlobalKey<AnimatedListState> _listKey = GlobalKey();
  final List<String> _displayedLogs = [];
  int _previousLength = 0;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _displayedLogs.addAll(widget.logs);
    _previousLength = widget.logs.length;
  }

  @override
  void didUpdateWidget(AnimatedLogsListView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.logs.length > _previousLength) {
      // ANIMATION: Add new logs with 100ms interval, FadeTransition + SizeTransition
      for (int i = _previousLength; i < widget.logs.length; i++) {
        Future.delayed(Duration(milliseconds: (i - _previousLength) * 100), () {
          if (mounted && i < widget.logs.length) {
            _displayedLogs.add(widget.logs[i]);
            _listKey.currentState?.insertItem(_displayedLogs.length - 1,
                duration: const Duration(milliseconds: 300));
          }
        });
      }
      _previousLength = widget.logs.length;
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final recentLogs =
        widget.logs.where((log) => log.contains('[E]')).take(10).toList();
    final displayLogs = recentLogs.isEmpty ? _displayedLogs : recentLogs;

    return AnimatedList(
      key: _listKey,
      initialItemCount: displayLogs.length,
      itemBuilder: (context, index, animation) {
        if (index >= displayLogs.length) return const SizedBox.shrink();
        final log = displayLogs[index];
        // ANIMATION: FadeTransition + SizeTransition for each log item
        return FadeTransition(
          opacity: animation,
          child: SizeTransition(
            sizeFactor: animation,
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
          ),
        );
      },
    );
  }
}
