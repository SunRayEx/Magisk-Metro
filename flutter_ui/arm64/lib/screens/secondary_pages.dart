import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/dashboard_providers.dart';
import '../models/models.dart';

class MagiskManagerPage extends ConsumerWidget {
  const MagiskManagerPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Magisk Manager', isDark),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(4),
                children: [
                  _buildMenuTile(
                    context,
                    'Install Magisk',
                    'Install or upgrade Magisk',
                    Icons.download,
                    () => _showInstallDialog(context),
                    isDark,
                  ),
                  _buildMenuTile(
                    context,
                    'Uninstall Magisk',
                    'Remove Magisk from device',
                    Icons.delete_forever,
                    () => _showUninstallDialog(context),
                    isDark,
                  ),
                  _buildMenuTile(
                    context,
                    'Patch Boot Image',
                    'Patch boot image manually',
                    Icons.construction,
                    () => _showPatchDialog(context),
                    isDark,
                  ),
                  _buildMenuTile(
                    context,
                    'Update Manager',
                    'Check for updates',
                    Icons.system_update,
                    () => _showUpdateDialog(context),
                    isDark,
                  ),
                  const SizedBox(height: 8),
                  _buildInfoCard(status, isDark),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          GestureDetector(
            onTap: () => Navigator.pop(context),
            child: Container(
              padding: const EdgeInsets.all(8),
              child: Icon(Icons.chevron_left,
                  color: AppTheme.getFont(isDark), size: 28),
            ),
          ),
          Expanded(
            child: Text(
              title,
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 20,
                color: AppTheme.getFont(isDark),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMenuTile(
    BuildContext context,
    String title,
    String subtitle,
    IconData icon,
    VoidCallback onTap,
    bool isDark,
  ) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 4),
        padding: const EdgeInsets.all(12),
        color: AppTheme.getListItem(isDark),
        child: Row(
          children: [
            Icon(icon, color: AppTheme.getListItemFont(isDark), size: 24),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 16,
                      color: AppTheme.getListItemFont(isDark),
                    ),
                  ),
                  Text(
                    subtitle,
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w400,
                      fontSize: 12,
                      color: AppTheme.getListItemFont(isDark)
                          .withValues(alpha: 0.6),
                    ),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right,
                color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6)),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoCard(MagiskStatus status, bool isDark) {
    return Container(
      padding: const EdgeInsets.all(16),
      color: isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Magisk Information',
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: 18,
              color: Colors.black,
            ),
          ),
          const SizedBox(height: 12),
          _buildInfoRow('Version', status.versionCode, isDark),
          _buildInfoRow('Root', status.isRooted ? 'Yes' : 'No', isDark),
          _buildInfoRow('Zygisk',
              status.isZygiskEnabled ? 'Enabled' : 'Disabled', isDark),
          _buildInfoRow('Ramdisk',
              status.isRamdiskLoaded ? 'Loaded' : 'Not Loaded', isDark),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                  color: Colors.black)),
          Text(value,
              style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  fontSize: 14,
                  color: Colors.black)),
        ],
      ),
    );
  }

  void _showInstallDialog(BuildContext context) =>
      _showDialog(context, 'Install Magisk', 'Select installation method:');
  void _showUninstallDialog(BuildContext context) =>
      _showDialog(context, 'Uninstall Magisk', 'Choose uninstallation method:');
  void _showPatchDialog(BuildContext context) =>
      _showDialog(context, 'Patch Boot Image', 'Select a boot image file:');
  void _showUpdateDialog(BuildContext context) =>
      _showDialog(context, 'Update Manager', 'Current version: 25.2');

  void _showDialog(BuildContext context, String title, String content) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
        title: Text(title,
            style: GoogleFonts.poppins(fontWeight: FontWeight.w900)),
        content: Text(content, style: GoogleFonts.poppins(fontSize: 14)),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel')),
        ],
      ),
    );
  }
}

class DenyListPage extends ConsumerWidget {
  const DenyListPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isEnabled = ref.watch(denyListEnabledProvider);
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'DenyList', isDark),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(4),
                children: [
                  Container(
                    color: AppTheme.getListItem(isDark),
                    child: SwitchListTile(
                      title: Text(
                        'Enable DenyList',
                        style: GoogleFonts.poppins(
                            fontWeight: FontWeight.w900,
                            fontSize: 16,
                            color: AppTheme.getListItemFont(isDark)),
                      ),
                      subtitle: Text(
                        'Hide root from specific apps',
                        style: GoogleFonts.poppins(
                            fontSize: 12,
                            color: AppTheme.getListItemFont(isDark)
                                .withValues(alpha: 0.6)),
                      ),
                      value: isEnabled,
                      onChanged: (value) => ref
                          .read(denyListEnabledProvider.notifier)
                          .state = value,
                      activeColor: const Color(0xFFFFC107),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          GestureDetector(
            onTap: () => Navigator.pop(context),
            child: Container(
                padding: const EdgeInsets.all(8),
                child: Icon(Icons.chevron_left,
                    color: AppTheme.getFont(isDark), size: 28)),
          ),
          Expanded(
              child: Text(title,
                  style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 20,
                      color: AppTheme.getFont(isDark)))),
        ],
      ),
    );
  }
}

class ModulesPage extends ConsumerWidget {
  const ModulesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final modules = ref.watch(modulesProvider);
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Modules', isDark),
            Expanded(
              child: modules.isEmpty
                  ? Center(
                      child: Text('No modules installed',
                          style: GoogleFonts.poppins(
                              fontSize: 16,
                              color: AppTheme.getFont(isDark)
                                  .withValues(alpha: 0.6))))
                  : ListView.builder(
                      padding: const EdgeInsets.all(4),
                      itemCount: modules.length,
                      itemBuilder: (context, index) {
                        final module = modules[index];
                        return Container(
                          margin: const EdgeInsets.only(bottom: 4),
                          color: AppTheme.getListItem(isDark),
                          child: ListTile(
                            title: Text(module.name,
                                style: GoogleFonts.poppins(
                                    fontWeight: FontWeight.w600,
                                    fontSize: 14,
                                    color: AppTheme.getListItemFont(isDark))),
                            trailing: Switch(
                                value: module.isEnabled,
                                onChanged: (value) {},
                                activeColor: const Color(0xFF4285F4)),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          GestureDetector(
              onTap: () => Navigator.pop(context),
              child: Container(
                  padding: const EdgeInsets.all(8),
                  child: Icon(Icons.chevron_left,
                      color: AppTheme.getFont(isDark), size: 28))),
          Expanded(
              child: Text(title,
                  style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 20,
                      color: AppTheme.getFont(isDark)))),
        ],
      ),
    );
  }
}

class AppsPage extends ConsumerWidget {
  const AppsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(appsProvider);
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Apps', isDark),
            Expanded(
              child: apps.isEmpty
                  ? Center(
                      child: Text('No apps configured',
                          style: GoogleFonts.poppins(
                              fontSize: 16,
                              color: AppTheme.getFont(isDark)
                                  .withValues(alpha: 0.6))))
                  : ListView.builder(
                      padding: const EdgeInsets.all(4),
                      itemCount: apps.length,
                      itemBuilder: (context, index) {
                        final app = apps[index];
                        return Container(
                          margin: const EdgeInsets.only(bottom: 4),
                          color: AppTheme.getListItem(isDark),
                          child: ListTile(
                            title: Text(app.name,
                                style: GoogleFonts.poppins(
                                    fontWeight: FontWeight.w600,
                                    fontSize: 14,
                                    color: AppTheme.getListItemFont(isDark))),
                            trailing: Switch(
                                value: app.isActive,
                                onChanged: (value) {},
                                activeColor: const Color(0xFFD32F2F)),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          GestureDetector(
              onTap: () => Navigator.pop(context),
              child: Container(
                  padding: const EdgeInsets.all(8),
                  child: Icon(Icons.chevron_left,
                      color: AppTheme.getFont(isDark), size: 28))),
          Expanded(
              child: Text(title,
                  style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 20,
                      color: AppTheme.getFont(isDark)))),
        ],
      ),
    );
  }
}

class LogsPage extends ConsumerWidget {
  const LogsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final logsAsync = ref.watch(logsProvider);
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Logs', isDark),
            Expanded(
              child: logsAsync.when(
                data: (logs) => ListView.builder(
                  padding: const EdgeInsets.all(4),
                  itemCount: logs.length,
                  itemBuilder: (context, index) => Container(
                    margin: const EdgeInsets.only(bottom: 2),
                    padding: const EdgeInsets.all(8),
                    color: AppTheme.getListItem(isDark),
                    child: Text(logs[index],
                        style: GoogleFonts.poppins(
                            fontSize: 10,
                            color: AppTheme.getListItemFont(isDark)),
                        overflow: TextOverflow.clip),
                  ),
                ),
                loading: () => Center(
                    child: CircularProgressIndicator(
                        color: AppTheme.getFont(isDark))),
                error: (error, stack) => Center(
                    child: Text('Error: $error',
                        style: GoogleFonts.poppins(
                            color: AppTheme.getFont(isDark)))),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          GestureDetector(
              onTap: () => Navigator.pop(context),
              child: Container(
                  padding: const EdgeInsets.all(8),
                  child: Icon(Icons.chevron_left,
                      color: AppTheme.getFont(isDark), size: 28))),
          Expanded(
              child: Text(title,
                  style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 20,
                      color: AppTheme.getFont(isDark)))),
        ],
      ),
    );
  }
}

class ContributorsPage extends ConsumerWidget {
  const ContributorsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final contributors = ref.watch(contributorsProvider);
    final isDark = ref.watch(themeProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Contributors', isDark),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.all(4),
                itemCount: contributors.length,
                itemBuilder: (context, index) {
                  final contributor = contributors[index];
                  return Container(
                    margin: const EdgeInsets.only(bottom: 4),
                    padding: const EdgeInsets.all(16),
                    color: AppTheme.getListItem(isDark),
                    child: Row(
                      children: [
                        Container(
                            width: 40,
                            height: 40,
                            color: const Color(0xFF9C27B0),
                            child: const Icon(Icons.person,
                                color: Colors.white, size: 20)),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(contributor.name,
                                  style: GoogleFonts.poppins(
                                      fontWeight: FontWeight.w900,
                                      fontSize: 16,
                                      color: AppTheme.getListItemFont(isDark))),
                              Text(contributor.platform,
                                  style: GoogleFonts.poppins(
                                      fontSize: 12,
                                      color: AppTheme.getListItemFont(isDark)
                                          .withValues(alpha: 0.6))),
                            ],
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          GestureDetector(
              onTap: () => Navigator.pop(context),
              child: Container(
                  padding: const EdgeInsets.all(8),
                  child: Icon(Icons.chevron_left,
                      color: AppTheme.getFont(isDark), size: 28))),
          Expanded(
              child: Text(title,
                  style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: 20,
                      color: AppTheme.getFont(isDark)))),
        ],
      ),
    );
  }
}

class SettingsSheet extends ConsumerWidget {
  SettingsSheet({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = ref.watch(themeProvider);

    return Container(
      decoration: BoxDecoration(
        color: AppTheme.getListItem(isDark),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(0)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            color: AppTheme.getTile(isDark),
            child: Row(
              children: [
                Text('Settings',
                    style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: 20,
                        color: AppTheme.getFont(isDark))),
                const Spacer(),
                GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: Icon(Icons.close, color: AppTheme.getFont(isDark))),
              ],
            ),
          ),
          _buildTile(Icons.dark_mode, 'Dark Mode',
              isDark ? 'Enabled' : 'Disabled', isDark,
              trailing: Switch(
                  value: isDark,
                  onChanged: (v) => ref.read(themeProvider.notifier).state = v,
                  activeColor: Colors.teal)),
          _buildTile(Icons.palette, 'Theme', isDark ? 'Dark' : 'Light', isDark,
              trailing: Icon(isDark ? Icons.dark_mode : Icons.light_mode,
                  color: AppTheme.getListItemFont(isDark))),
          const SizedBox(height: 20),
        ],
      ),
    );
  }

  Widget _buildTile(IconData icon, String title, String subtitle, bool isDark,
      {Widget? trailing}) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.all(12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                  color: isDark ? Colors.white12 : Colors.black12,
                  borderRadius: BorderRadius.circular(8))),
          const SizedBox(width: 12),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text(title,
                    style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w600,
                        fontSize: 16,
                        color: AppTheme.getListItemFont(isDark))),
                Text(subtitle,
                    style: GoogleFonts.poppins(
                        fontSize: 12,
                        color: AppTheme.getListItemFont(isDark)
                            .withValues(alpha: 0.6))),
              ])),
          if (trailing != null) trailing,
        ],
      ),
    );
  }
}
