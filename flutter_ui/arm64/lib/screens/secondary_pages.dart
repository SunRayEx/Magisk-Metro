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
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Install Magisk'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Choose installation method:'),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.smartphone),
              title: const Text('Auto Install'),
              subtitle: const Text('Automatically detect and install'),
              onTap: () {
                Navigator.pop(context);
                // Call the actual install function
                _performInstallMagisk(context, '');
              },
            ),
            ListTile(
              leading: const Icon(Icons.sd_card),
              title: const Text('Manual Install'),
              subtitle: const Text('Select boot image file'),
              onTap: () {
                Navigator.pop(context);
                // This would typically open a file picker
                // For now, we'll just show a message
                _showMessage(context, 'Manual install would open file picker');
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }

  void _showUninstallDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Uninstall Magisk'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Choose uninstall option:'),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.restore),
              title: const Text('Full Uninstall'),
              subtitle: const Text('Remove Magisk and restore images'),
              onTap: () {
                Navigator.pop(context);
                _performUninstallMagisk(context, true);
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete),
              title: const Text('Remove Only'),
              subtitle: const Text('Remove Magisk without restoring images'),
              onTap: () {
                Navigator.pop(context);
                _performUninstallMagisk(context, false);
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }

  void _showPatchDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Patch Boot Image'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Choose patching method:'),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.sd_card),
              title: const Text('Select Boot Image'),
              subtitle: const Text('Choose boot image file to patch'),
              onTap: () {
                Navigator.pop(context);
                // This would typically open a file picker
                _showMessage(context, 'File picker would open for boot image selection');
              },
            ),
            ListTile(
              leading: const Icon(Icons.auto_fix_high),
              title: const Text('Auto Detect'),
              subtitle: const Text('Automatically detect and patch boot image'),
              onTap: () {
                Navigator.pop(context);
                _performPatchBootImage(context, '');
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }

  void _showUpdateDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Update Manager'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Choose update method:'),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.download),
              title: const Text('Download & Install'),
              subtitle: const Text('Download latest version and install'),
              onTap: () {
                Navigator.pop(context);
                _performUpdateManager(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.info),
              title: const Text('Check Version'),
              subtitle: const Text('Check latest available version'),
              onTap: () {
                Navigator.pop(context);
                _checkLatestVersion(context);
              },
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }

  void _performInstallMagisk(BuildContext context, String bootImage) {
    // This would call the actual AndroidDataService method
    // For now, we'll just show a message
    _showMessage(context, 'Install Magisk initiated with boot image: $bootImage');
  }

  void _performUninstallMagisk(BuildContext context, bool restoreImages) {
    _showMessage(context, 'Uninstall Magisk initiated with restoreImages: $restoreImages');
  }

  void _performPatchBootImage(BuildContext context, String bootImage) {
    _showMessage(context, 'Patch Boot Image initiated with boot image: $bootImage');
  }

  void _performUpdateManager(BuildContext context) {
    _showMessage(context, 'Update Manager initiated');
  }

  void _checkLatestVersion(BuildContext context) {
    _showMessage(context, 'Checking latest version...');
  }

  void _showMessage(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}

class DenyListPage extends ConsumerStatefulWidget {
  const DenyListPage({super.key});

  @override
  ConsumerState<DenyListPage> createState() => _DenyListPageState();
}

class _DenyListPageState extends ConsumerState<DenyListPage> {
  @override
  Widget build(BuildContext context) {
    final apps = ref.watch(appsProvider);
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
              child: ListView.builder(
                padding: const EdgeInsets.all(4),
                itemCount: apps.length,
                itemBuilder: (context, index) {
                  final app = apps[index];
                  // isActive = false means app is in denylist (root hidden)
                  // isActive = true means app is not in denylist (root visible)
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
                      subtitle: Text(
                        app.packageName,
                        style: GoogleFonts.poppins(
                          fontSize: 12,
                          color: AppTheme.getListItemFont(isDark)
                              .withValues(alpha: 0.6),
                        ),
                      ),
                      trailing: Switch(
                        value: !app.isActive, // Show ON when in denylist (root hidden)
                        onChanged: (value) {
                          // value = true means add to denylist (hide root)
                          // value = false means remove from denylist (show root)
                          ref.read(appsProvider.notifier).toggleApp(app.packageName, !value);
                        },
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
              child: modules.isEmpty
                  ? Center(
                      child: Text(
                        'No modules installed',
                        style: GoogleFonts.poppins(
                          fontSize: 16,
                          color: AppTheme.getFont(isDark),
                        ),
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: () async {
                        // Trigger a refresh by calling the provider's method
                        // Since we don't have a direct refresh method, we'll just reload
                        // by calling the same method again
                      },
                      child: ListView.builder(
                        padding: const EdgeInsets.all(4),
                        itemCount: modules.length,
                        itemBuilder: (context, index) {
                          final module = modules[index];
                          return Container(
                            margin: const EdgeInsets.only(bottom: 4),
                            color: AppTheme.getListItem(isDark),
                            child: ExpansionTile(
                              leading: Icon(Icons.extension, color: widgetColor),
                              title: Text(
                                module.name,
                                style: GoogleFonts.poppins(
                                  fontWeight: FontWeight.w900,
                                  color: AppTheme.getListItemFont(isDark),
                                ),
                              ),
                              subtitle: Text(
                                '${module.version} • ${module.author}',
                                style: GoogleFonts.poppins(
                                  fontSize: 12,
                                  color: AppTheme.getListItemFont(isDark)
                                      .withValues(alpha: 0.6),
                                ),
                              ),
                              trailing: Switch(
                                value: module.isEnabled,
                                onChanged: (value) {
                                  ref
                                      .read(modulesProvider.notifier)
                                      .toggleModule(module.name, value);
                                },
                                activeColor: widgetColor,
                              ),
                              children: [
                                Container(
                                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      if (module.description.isNotEmpty)
                                        Text(
                                          module.description,
                                          style: GoogleFonts.poppins(
                                            fontSize: 14,
                                            color: AppTheme.getListItemFont(isDark)
                                                .withValues(alpha: 0.8),
                                          ),
                                        ),
                                      const SizedBox(height: 8),
                                      Text(
                                        'Path: ${module.path}',
                                        style: GoogleFonts.poppins(
                                          fontSize: 12,
                                          color: AppTheme.getListItemFont(isDark)
                                              .withValues(alpha: 0.6),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
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

class AppsPage extends ConsumerStatefulWidget {
  const AppsPage({super.key});

  @override
  ConsumerState<AppsPage> createState() => _AppsPageState();
}

class _AppsPageState extends ConsumerState<AppsPage> {
  @override
  Widget build(BuildContext context) {
    final apps = ref.watch(appsProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(4, tileColorIndex, isDark);

    // Filter apps to show only those with root access granted
    final rootApps = apps.where((app) => app.hasRootAccess).toList();

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, 'Root Access', isDark),
            rootApps.isEmpty
                ? Expanded(
                    child: Center(
                      child: Text(
                        'No apps with root access',
                        style: GoogleFonts.poppins(
                          fontSize: 16,
                          color: AppTheme.getFont(isDark),
                        ),
                      ),
                    ),
                  )
                : Expanded(
                    child: ListView.builder(
                      padding: const EdgeInsets.all(4),
                      itemCount: rootApps.length,
                      itemBuilder: (context, index) {
                        final app = rootApps[index];
                        return Container(
                          margin: const EdgeInsets.only(bottom: 4),
                          color: AppTheme.getListItem(isDark),
                          child: ListTile(
                            leading: Icon(Icons.security, color: widgetColor),
                            title: Text(
                              app.name,
                              style: GoogleFonts.poppins(
                                fontWeight: FontWeight.w900,
                                color: AppTheme.getListItemFont(isDark),
                              ),
                            ),
                            subtitle: Text(
                              app.packageName,
                              style: GoogleFonts.poppins(
                                fontSize: 12,
                                color: AppTheme.getListItemFont(isDark)
                                    .withValues(alpha: 0.6),
                              ),
                            ),
                            trailing: Switch(
                              value: app.hasRootAccess,
                              onChanged: (value) {
                                ref.read(appsProvider.notifier)
                                    .toggleRootAccess(app.packageName, value);
                              },
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
