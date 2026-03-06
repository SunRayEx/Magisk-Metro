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
        child: Column(
          children: [
            _buildTopBar(),
            Expanded(
              child: _buildMainContent(ref),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.all(24.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Icon(
            Icons.settings,
            color: Colors.white,
            size: 28,
          ),
        ],
      ),
    );
  }

  Widget _buildMainContent(WidgetRef ref) {
    return Row(
      children: [
        Expanded(
          flex: 20,
          child: _buildLeftColumn(ref),
        ),
        Expanded(
          flex: 11,
          child: _buildRightColumn(ref),
        ),
      ],
    );
  }

  Widget _buildLeftColumn(WidgetRef ref) {
    return Column(
      children: [
        Expanded(flex: 2, child: _buildMagiskCard(ref)),
        const SizedBox(height: 3),
        Expanded(flex: 1, child: _buildDenyListCard(ref)),
        const SizedBox(height: 3),
        Expanded(flex: 1, child: _buildContributorCard(ref)),
      ],
    );
  }

  Widget _buildRightColumn(WidgetRef ref) {
    return Column(
      children: [
        Expanded(flex: 1, child: _buildModulesCard(ref)),
        const SizedBox(height: 3),
        Expanded(flex: 1, child: _buildAppsCard(ref)),
        const SizedBox(height: 3),
        Expanded(flex: 2, child: _buildLogsCard(ref)),
      ],
    );
  }

  Widget _buildMagiskCard(WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);

    return Container(
      color: const Color(0xFF009688),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 60,
                  height: 60,
                  color: Colors.white,
                  child: const Icon(
                    Icons.face,
                    size: 40,
                    color: Color(0xFF009688),
                  ),
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Magisk ${status.versionCode}',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: 28,
                        color: const Color(0xFF000000),
                      ),
                    ),
                    Text(
                      '[${status.isRooted ? "enable" : "disable"}]',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                        color: const Color(0xFF000000),
                      ),
                    ),
                  ],
                ),
              ],
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Root Status : ${status.isRooted ? "Yes" : "No"}',
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w900,
                    fontSize: 16,
                    color: const Color(0xFF000000),
                  ),
                ),
                Text(
                  'Zygisk Status : ${status.isZygiskEnabled ? "Yes" : "No"}',
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w900,
                    fontSize: 16,
                    color: const Color(0xFF000000),
                  ),
                ),
                Text(
                  'Ramdisk Status : ${status.isRamdiskLoaded ? "Yes" : "No"}',
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w900,
                    fontSize: 16,
                    color: const Color(0xFF000000),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDenyListCard(WidgetRef ref) {
    final isEnabled = ref.watch(denyListEnabledProvider);

    return Container(
      color: const Color(0xFFFFC107),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Align(
          alignment: Alignment.topLeft,
          child: Text(
            isEnabled ? 'DenyList' : 'DenyList [OFF]',
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: 28,
              color: const Color(0xFF000000),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContributorCard(WidgetRef ref) {
    final contributors = ref.watch(contributorsProvider);

    return Container(
      color: const Color(0xFF9C27B0),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Contributor',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 28,
                color: const Color(0xFF000000),
              ),
            ),
            const SizedBox(height: 8),
            ...contributors.map((c) => Text(
                  '${c.name} <${c.platform}>',
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w600,
                    fontSize: 12,
                    color: const Color(0xFF000000),
                  ),
                )),
          ],
        ),
      ),
    );
  }

  Widget _buildModulesCard(WidgetRef ref) {
    final modules = ref.watch(modulesProvider);

    return Container(
      color: const Color(0xFF4285F4),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              'Modules',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 24,
                color: const Color(0xFF000000),
              ),
            ),
            const Spacer(),
            ...modules.take(3).map((m) => Text(
                  m.name,
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w500,
                    fontSize: 11,
                    color: const Color(0xFF000000),
                  ),
                  textAlign: TextAlign.right,
                )),
            if (modules.length > 3)
              Text(
                '...',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w500,
                  fontSize: 11,
                  color: const Color(0xFF000000),
                ),
                textAlign: TextAlign.right,
              ),
            const Spacer(),
            Align(
              alignment: Alignment.bottomRight,
              child: Text(
                '${modules.length}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 56,
                  color: const Color(0xFF000000),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppsCard(WidgetRef ref) {
    final apps = ref.watch(appsProvider);

    return Container(
      color: const Color(0xFFD32F2F),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              'Apps',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 24,
                color: const Color(0xFF000000),
              ),
            ),
            const Spacer(),
            ...apps.take(3).map((a) => Text(
                  a.name,
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w500,
                    fontSize: 11,
                    color: const Color(0xFF000000),
                  ),
                  textAlign: TextAlign.right,
                )),
            if (apps.length > 3)
              Text(
                '...',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w500,
                  fontSize: 11,
                  color: const Color(0xFF000000),
                ),
                textAlign: TextAlign.right,
              ),
            const Spacer(),
            Align(
              alignment: Alignment.bottomRight,
              child: Text(
                '${apps.length}',
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 56,
                  color: const Color(0xFF000000),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLogsCard(WidgetRef ref) {
    final logsAsync = ref.watch(logsProvider);

    return Container(
      color: const Color(0xFFFFFFFF),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Logs',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 28,
                color: const Color(0xFF000000),
              ),
            ),
            const SizedBox(height: 8),
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
                    fontSize: 10,
                    color: const Color(0xFF000000),
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
    return ListView.builder(
      controller: _scrollController,
      itemCount: widget.logs.length,
      itemBuilder: (context, index) {
        return Text(
          widget.logs[index],
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w500,
            fontSize: 10,
            color: const Color(0xFF000000),
          ),
          overflow: TextOverflow.clip,
          maxLines: 1,
        );
      },
    );
  }
}
