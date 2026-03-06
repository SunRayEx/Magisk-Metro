import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'providers/dashboard_providers.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: const Color(0xFF000000),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              _buildTopBar(),
              Expanded(
                child: _buildMainContent(context, ref),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _navigateTo(BuildContext context, String route) {
    Navigator.pushNamed(context, route);
  }

  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Icon(
            Icons.settings,
            color: Colors.white,
            size: 24,
          ),
        ],
      ),
    );
  }

  Widget _buildMainContent(BuildContext context, WidgetRef ref) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: _buildLeftColumn(context, ref),
        ),
        const SizedBox(width: 4),
        Expanded(
          child: _buildRightColumn(context, ref),
        ),
      ],
    );
  }

  Widget _buildLeftColumn(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(flex: 6, child: _buildMagiskCard(context, ref)),
        const SizedBox(height: 4),
        Expanded(flex: 3, child: _buildDenyListCard(context, ref)),
        const SizedBox(height: 4),
        Expanded(flex: 3, child: _buildContributorCard(context, ref)),
      ],
    );
  }

  Widget _buildRightColumn(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(flex: 4, child: _buildModulesCard(context, ref)),
        const SizedBox(height: 4),
        Expanded(flex: 4, child: _buildAppsCard(context, ref)),
        const SizedBox(height: 4),
        Expanded(flex: 4, child: _buildLogsCard(context, ref)),
      ],
    );
  }

  Widget _buildMagiskCard(BuildContext context, WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/magisk'),
      child: Container(
        color: const Color(0xFF009688),
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Column(
            children: [
              Container(
                width: 60,
                height: 60,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Center(
                  child: Text(
                    'M',
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 32,
                      color: const Color(0xFF009688),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Magisk ${status.versionCode}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 16,
                  color: const Color(0xFF000000),
                ),
              ),
              Text(
                '[${status.isRooted ? "enable" : "disable"}]',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w600,
                  fontSize: 12,
                  color: const Color(0xFF000000),
                ),
              ),
              const Spacer(),
              _buildStatusRow('Root', status.isRooted),
              _buildStatusRow('Zygisk', status.isZygiskEnabled),
              _buildStatusRow('Ramdisk', status.isRamdiskLoaded),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusRow(String label, bool value) {
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
              color: const Color(0xFF000000),
            ),
          ),
          Text(
            value ? 'Yes' : 'No',
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: 10,
              color: const Color(0xFF000000),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDenyListCard(BuildContext context, WidgetRef ref) {
    final isEnabled = ref.watch(denyListEnabledProvider);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/denylist'),
      child: Container(
        color: const Color(0xFFFFC107),
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                isEnabled ? 'DenyList' : 'DenyList [OFF]',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 14,
                  color: const Color(0xFF000000),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContributorCard(BuildContext context, WidgetRef ref) {
    final contributors = ref.watch(contributorsProvider);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/contributors'),
      child: Container(
        color: const Color(0xFF9C27B0),
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                'Contributor',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 12,
                  color: const Color(0xFF000000),
                ),
              ),
              const Spacer(),
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: contributors
                        .map((c) => Text(
                              c.name,
                              style: GoogleFonts.poppins(
                                fontWeight: FontWeight.w500,
                                fontSize: 9,
                                color: const Color(0xFF000000),
                              ),
                            ))
                        .toList(),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildModulesCard(BuildContext context, WidgetRef ref) {
    final modules = ref.watch(modulesProvider);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/modules'),
      child: Container(
        color: const Color(0xFF4285F4),
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                'Modules',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 12,
                  color: const Color(0xFF000000),
                ),
              ),
              const Spacer(),
              Expanded(
                child: Center(
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        ...modules.take(2).map((m) => Text(
                              m.name,
                              style: GoogleFonts.poppins(
                                fontWeight: FontWeight.w500,
                                fontSize: 8,
                                color: const Color(0xFF000000),
                              ),
                            )),
                        if (modules.length > 2)
                          Text(
                            '...',
                            style: GoogleFonts.poppins(
                              fontWeight: FontWeight.w500,
                              fontSize: 8,
                              color: const Color(0xFF000000),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
              Text(
                '${modules.length}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 24,
                  color: const Color(0xFF000000),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAppsCard(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(appsProvider);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/apps'),
      child: Container(
        color: const Color(0xFFD32F2F),
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                'Apps',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 12,
                  color: const Color(0xFF000000),
                ),
              ),
              const Spacer(),
              Expanded(
                child: Center(
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        ...apps.take(2).map((a) => Text(
                              a.name,
                              style: GoogleFonts.poppins(
                                fontWeight: FontWeight.w500,
                                fontSize: 8,
                                color: const Color(0xFF000000),
                              ),
                            )),
                        if (apps.length > 2)
                          Text(
                            '...',
                            style: GoogleFonts.poppins(
                              fontWeight: FontWeight.w500,
                              fontSize: 8,
                              color: const Color(0xFF000000),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
              Text(
                '${apps.length}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 24,
                  color: const Color(0xFF000000),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLogsCard(BuildContext context, WidgetRef ref) {
    final logsAsync = ref.watch(logsProvider);

    return GestureDetector(
      onTap: () => _navigateTo(context, '/logs'),
      child: Container(
        color: const Color(0xFFFFFFFF),
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Logs',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 14,
                  color: const Color(0xFF000000),
                ),
              ),
              const SizedBox(height: 4),
              Expanded(
                child: logsAsync.when(
                  data: (logs) => _LogsListView(logs: logs),
                  loading: () => const Center(
                    child: CircularProgressIndicator(
                      color: Colors.black,
                    ),
                  ),
                  error: (error, stack) => Text(
                    '[E] Error: $error',
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w500,
                      fontSize: 8,
                      color: const Color(0xFF000000),
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

  const _LogsListView({required this.logs});

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
            color: const Color(0xFF000000),
          ),
          overflow: TextOverflow.clip,
          maxLines: 1,
        );
      },
    );
  }
}
