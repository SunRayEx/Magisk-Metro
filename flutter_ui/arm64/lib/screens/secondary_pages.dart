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
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(0, tileColorIndex, isDark);

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
                    widgetColor,
                    () => _showInstallDialog(context),
                    isDark,
                  ),
                  _buildMenuTile(
                    context,
                    'Uninstall Magisk',
                    'Remove Magisk from device',
                    Icons.delete_forever,
                    widgetColor,
                    () => _showUninstallDialog(context),
                    isDark,
                  ),
                  _buildMenuTile(
                    context,
                    'Patch Boot Image',
                    'Patch boot image manually',
                    Icons.construction,
                    widgetColor,
                    () => _showPatchDialog(context),
                    isDark,
                  ),
                  _buildMenuTile(
                    context,
                    'Update Manager',
                    'Check for updates',
                    Icons.system_update,
                    widgetColor,
                    () => _showUpdateDialog(context),
                    isDark,
                  ),
                  const SizedBox(height: 8),
                  _buildInfoCard(status, isDark, widgetColor),
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
    Color widgetColor,
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
            Icon(icon, color: widgetColor, size: 24),
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

  Widget _buildInfoCard(MagiskStatus status, bool isDark, Color widgetColor) {
    return Container(
      padding: const EdgeInsets.all(16),
      color: widgetColor,
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
          _buildInfoRow('Version', status.versionCode),
          _buildInfoRow('Root', status.isRooted ? 'Yes' : 'No'),
          _buildInfoRow(
              'Zygisk', status.isZygiskEnabled ? 'Enabled' : 'Disabled'),
          _buildInfoRow(
              'Ramdisk', status.isRamdiskLoaded ? 'Loaded' : 'Not Loaded'),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
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

  void _showInstallDialog(BuildContext context) {
    _showDialog(
        context, 'Install Magisk', 'This will install Magisk on your device.');
  }

  void _showUninstallDialog(BuildContext context) {
    _showDialog(context, 'Uninstall Magisk',
        'This will remove Magisk from your device.');
  }

  void _showPatchDialog(BuildContext context) {
    _showDialog(context, 'Patch Boot Image', 'Select a boot image to patch.');
  }

  void _showUpdateDialog(BuildContext context) {
    _showDialog(context, 'Update Manager', 'Checking for updates...');
  }

  void _showDialog(BuildContext context, String title, String message) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
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
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(1, tileColorIndex, isDark);

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
                      activeColor: widgetColor,
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
}

class ModulesPage extends ConsumerWidget {
  const ModulesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final modules = ref.watch(modulesProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(3, tileColorIndex, isDark);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Modules', isDark),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.all(4),
                itemCount: modules.length,
                itemBuilder: (context, index) {
                  final module = modules[index];
                  return Container(
                    margin: const EdgeInsets.only(bottom: 4),
                    color: AppTheme.getListItem(isDark),
                    child: ListTile(
                      leading: Icon(Icons.extension, color: widgetColor),
                      title: Text(
                        module.name,
                        style: GoogleFonts.poppins(
                          fontWeight: FontWeight.w900,
                          color: AppTheme.getListItemFont(isDark),
                        ),
                      ),
                      trailing: Switch(
                        value: module.isEnabled,
                        onChanged: (value) {},
                        activeColor: widgetColor,
                      ),
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
}

class AppsPage extends ConsumerWidget {
  const AppsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(appsProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(4, tileColorIndex, isDark);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Apps', isDark),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.all(4),
                itemCount: apps.length,
                itemBuilder: (context, index) {
                  final app = apps[index];
                  return Container(
                    margin: const EdgeInsets.only(bottom: 4),
                    color: AppTheme.getListItem(isDark),
                    child: ListTile(
                      leading: Icon(Icons.apps, color: widgetColor),
                      title: Text(
                        app.name,
                        style: GoogleFonts.poppins(
                          fontWeight: FontWeight.w900,
                          color: AppTheme.getListItemFont(isDark),
                        ),
                      ),
                      trailing: Switch(
                        value: app.isActive,
                        onChanged: (value) {},
                        activeColor: widgetColor,
                      ),
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
                  itemBuilder: (context, index) {
                    return Container(
                      margin: const EdgeInsets.only(bottom: 2),
                      padding: const EdgeInsets.all(8),
                      color: AppTheme.getListItem(isDark),
                      child: Text(
                        logs[index],
                        style: GoogleFonts.poppins(
                          fontSize: 12,
                          color: AppTheme.getListItemFont(isDark),
                        ),
                      ),
                    );
                  },
                ),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, stack) => Center(
                  child: Text('Error: $error'),
                ),
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
}

class ContributorsPage extends ConsumerWidget {
  const ContributorsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final contributors = ref.watch(contributorsProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(2, tileColorIndex, isDark);

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
                    color: AppTheme.getListItem(isDark),
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: widgetColor,
                        child: Text(
                          contributor.name.isNotEmpty
                              ? contributor.name[0].toUpperCase()
                              : '?',
                          style: const TextStyle(color: Colors.white),
                        ),
                      ),
                      title: Text(
                        contributor.name,
                        style: GoogleFonts.poppins(
                          fontWeight: FontWeight.w900,
                          color: AppTheme.getListItemFont(isDark),
                        ),
                      ),
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
}

class ThemePage extends ConsumerWidget {
  const ThemePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = ref.watch(themeProvider);
    final selectedColorIndex = ref.watch(tileColorProvider);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Theme', isDark),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.all(4),
                itemCount: AppTheme.tileColorNames.length,
                itemBuilder: (context, index) {
                  final isSelected = index == selectedColorIndex;
                  return GestureDetector(
                    onTap: () =>
                        ref.read(tileColorProvider.notifier).state = index,
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 4),
                      padding: const EdgeInsets.all(16),
                      color: AppTheme.getListItem(isDark),
                      child: Row(
                        children: [
                          Container(
                            width: 32,
                            height: 32,
                            decoration: BoxDecoration(
                              color: index == 0
                                  ? (isDark
                                      ? const Color(0xFF009688)
                                      : const Color(0xFF4DB6AC))
                                  : AppTheme.tileColors[index],
                              borderRadius: BorderRadius.circular(4),
                              border: isSelected
                                  ? Border.all(color: Colors.white, width: 2)
                                  : null,
                            ),
                            child: isSelected
                                ? const Icon(Icons.check,
                                    color: Colors.white, size: 20)
                                : null,
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Text(
                              AppTheme.tileColorNames[index],
                              style: GoogleFonts.poppins(
                                fontWeight: FontWeight.w600,
                                fontSize: 16,
                                color: AppTheme.getListItemFont(isDark),
                              ),
                            ),
                          ),
                          if (isSelected)
                            Icon(
                              Icons.check_circle,
                              color: AppTheme.getListItemFont(isDark),
                            ),
                        ],
                      ),
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
}

class SettingsSheet extends ConsumerWidget {
  final VoidCallback? onThemeTap;

  const SettingsSheet({super.key, this.onThemeTap});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getWidgetColor(tileColorIndex, isDark);

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
          _buildTile(Icons.nightlight_round, 'Dark Mode',
              isDark ? 'Enabled' : 'Disabled', isDark,
              trailing: Switch(
                  value: isDark,
                  onChanged: (v) => ref.read(themeProvider.notifier).state = v,
                  activeColor: widgetColor)),
          GestureDetector(
            onTap: onThemeTap,
            child: _buildTile(
                Icons.palette, 'Theme', 'Change widget color', isDark,
                trailing: Icon(Icons.chevron_right,
                    color: AppTheme.getListItemFont(isDark))),
          ),
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
          Icon(icon, color: AppTheme.getListItemFont(isDark), size: 24),
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
