import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:url_launcher/url_launcher.dart';
import '../providers/dashboard_providers.dart';
import '../models/models.dart';
import '../services/android_data_service.dart';
import 'flash_logs_page.dart';
import 'module_webview_page.dart';
import '../navigation/flip_page_route.dart';
import '../l10n/app_localizations.dart';

class MagiskManagerPage extends ConsumerWidget {
  const MagiskManagerPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(0, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;
    
    // Check if we have proper Magisk root
    final hasMagiskRoot = status.isRooted;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.magiskManager, isDark),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(4),
                children: [
                  // Install Magisk - always available (contains patchBootImage for non-rooted)
                  _buildMenuTile(
                    context,
                    localizations.installMagisk,
                    localizations.installMagiskDesc,
                    Icons.download,
                    widgetColor,
                    () => _showInstallDialog(context, status),
                    isDark,
                  ),
                  // Uninstall Magisk - only if rooted with Magisk
                  if (hasMagiskRoot)
                    _buildMenuTile(
                      context,
                      localizations.uninstallMagisk,
                      localizations.uninstallMagiskDesc,
                      Icons.delete_forever,
                      widgetColor,
                      () => _showUninstallDialog(context),
                      isDark,
                    ),
                  // Update Manager - always available
                  _buildMenuTile(
                    context,
                    localizations.updateManager,
                    localizations.updateManagerDesc,
                    Icons.system_update,
                    widgetColor,
                    () => _showUpdateDialog(context),
                    isDark,
                  ),
                  const SizedBox(height: 8),
                  _buildInfoCard(status, isDark, widgetColor, localizations),
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

  Widget _buildInfoCard(MagiskStatus status, bool isDark, Color widgetColor, AppLocalizations localizations) {
    return Container(
      padding: const EdgeInsets.all(16),
      color: widgetColor,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.magiskInfo,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: 18,
              color: Colors.black,
            ),
          ),
          const SizedBox(height: 12),
          _buildInfoRow(localizations.version, status.versionCode),
          _buildInfoRow(localizations.root, status.isRooted ? localizations.yes : localizations.no),
          _buildInfoRow(
              localizations.zygisk, status.isZygiskEnabled ? localizations.enabled : localizations.disabled),
          _buildInfoRow(
              localizations.ramdisk, status.isRamdiskLoaded ? localizations.loaded : localizations.notLoaded),
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

  void _showInstallDialog(BuildContext context, MagiskStatus status) {
    final localizations = AppLocalizations.of(context)!;
    final hasRoot = status.isRooted;
    
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(localizations.installMagisk),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Only show auto install if we have Magisk root access
            if (hasRoot)
              ListTile(
                leading: const Icon(Icons.smartphone),
                title: Text(localizations.autoInstall),
                subtitle: Text(localizations.autoInstallDesc),
                onTap: () {
                  Navigator.pop(dialogContext);
                  _performInstallMagisk(context, '', isPatchMode: false);
                },
              ),
            // patchBootImage is always available (doesn't require root for selecting file)
            ListTile(
              leading: const Icon(Icons.sd_card),
              title: Text(localizations.patchBootImage),
              subtitle: Text(localizations.patchBootImageDesc),
              onTap: () {
                Navigator.pop(dialogContext);
                _selectFileAndPatch(context);
              },
            ),
            // Only show OTA slot switch if we have Magisk root access
            if (hasRoot)
              ListTile(
                leading: const Icon(Icons.swap_horiz),
                title: Text(localizations.otaSlotSwitch),
                subtitle: Text(localizations.otaSlotSwitchDesc),
                onTap: () {
                  Navigator.pop(dialogContext);
                  _performOtaSlotSwitch(context);
                },
              ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(localizations.cancel),
          ),
        ],
      ),
    );
  }

  /// Select a file and navigate to patch page
  /// This is separated to ensure proper async handling
  void _selectFileAndPatch(BuildContext context) async {
    final localizations = AppLocalizations.of(context)!;
    
    try {
      debugPrint('Opening file picker...');
      final filePath = await AndroidDataService.pickFile();
      debugPrint('Selected file path: $filePath');
      
      if (filePath != null && filePath.isNotEmpty) {
        debugPrint('Navigating to FlashLogsPage for patching with file: $filePath');
        _performInstallMagisk(context, filePath, isPatchMode: true);
      } else {
        debugPrint('File selection cancelled or empty path');
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(localizations.noFileSelected)),
          );
        }
      }
    } catch (e) {
      debugPrint('Error selecting file: $e');
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error selecting file: $e')),
        );
      }
    }
  }

  void _showUninstallDialog(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(localizations.uninstallMagisk),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.restore),
              title: Text(localizations.fullUninstall),
              subtitle: Text(localizations.fullUninstallDesc),
              onTap: () {
                Navigator.pop(context);
                _performUninstallMagisk(context, true);
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete),
              title: Text(localizations.removeOnly),
              subtitle: Text(localizations.removeOnlyDesc),
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
            child: Text(localizations.cancel),
          ),
        ],
      ),
    );
  }

  void _showUpdateDialog(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(localizations.updateManager),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.download),
              title: Text(localizations.downloadInstall),
              subtitle: Text(localizations.downloadInstallDesc),
              onTap: () {
                Navigator.pop(context);
                _performUpdateManager(context);
              },
            ),
            ListTile(
              leading: const Icon(Icons.info),
              title: Text(localizations.checkVersion),
              subtitle: Text(localizations.checkVersionDesc),
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
            child: Text(localizations.cancel),
          ),
        ],
      ),
    );
  }

  void _performInstallMagisk(BuildContext context, String bootImage, {bool isPatchMode = false}) {
    final localizations = AppLocalizations.of(context)!;
    final title = isPatchMode ? localizations.patchBootImage : localizations.installMagisk;
    
    Navigator.push(
      context,
      FlipPageRoute(
        page: FlashLogsPage(
          title: title,
          onExecute: () async {
            try {
              final result = await AndroidDataService.installMagisk(
                bootImage: bootImage.isEmpty ? null : bootImage,
                isPatchMode: isPatchMode,
              );
              return result;
            } catch (e) {
              return false;
            }
          },
        ),
      ),
    );
  }

  void _performUninstallMagisk(BuildContext context, bool restoreImages) {
    final localizations = AppLocalizations.of(context)!;
    final title = restoreImages ? localizations.fullUninstall : localizations.removeOnly;
    Navigator.push(
      context,
      FlipPageRoute(
        page: FlashLogsPage(
          title: title,
          onExecute: () async {
            try {
              final result = await AndroidDataService.uninstallMagisk(restoreImages: restoreImages);
              return result;
            } catch (e) {
              return false;
            }
          },
        ),
      ),
    );
  }


  void _performUpdateManager(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    Navigator.push(
      context,
      FlipPageRoute(
        page: FlashLogsPage(
          title: localizations.updateManager,
          onExecute: () async {
            try {
              final result = await AndroidDataService.updateManager();
              return result;
            } catch (e) {
              return false;
            }
          },
        ),
      ),
    );
  }

  void _checkLatestVersion(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    _showMessage(context, localizations.checkVersion);
  }

  void _showMessage(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  void _performOtaSlotSwitch(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    Navigator.push(
      context,
      FlipPageRoute(
        page: FlashLogsPage(
          title: localizations.otaSlotSwitch,
          onExecute: () async {
            try {
              final result = await AndroidDataService.otaSlotSwitch();
              return result;
            } catch (e) {
              return false;
            }
          },
        ),
      ),
    );
  }
}

class SettingsPage extends ConsumerStatefulWidget {
  const SettingsPage({super.key});

  @override
  ConsumerState<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends ConsumerState<SettingsPage> {
  bool _isZygiskEnabled = false;
  bool _isSuListEnabled = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    try {
      final zygiskEnabled = await AndroidDataService.isZygiskEnabled();
      final suListEnabled = await AndroidDataService.isSuListEnabled();
      setState(() {
        _isZygiskEnabled = zygiskEnabled;
        _isSuListEnabled = suListEnabled;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _toggleZygisk(bool enabled) async {
    if (!mounted) return;
    setState(() {
      _isZygiskEnabled = enabled;
    });
    
    try {
      final success = await AndroidDataService.setZygiskEnabled(enabled);
      if (!mounted) return;
      
      if (success) {
        ref.refresh(appsProvider);
        if (enabled && mounted) {
          _showRestartDialog();
        }
        // If Zygisk is disabled, also disable DenyList
        if (!enabled) {
          await AndroidDataService.setDenyListEnabled(false);
        }
      } else {
        setState(() {
          _isZygiskEnabled = !enabled;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isZygiskEnabled = !enabled;
      });
    }
  }

  void _showRestartDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Restart Required'),
        content: Text('The changes require a device restart to take effect.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Later'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              AndroidDataService.rebootDevice();
            },
            child: Text('Restart Now'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(1, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;
    final status = ref.watch(magiskStatusProvider);
    
    // Check if we have MagiskSU root (only show Zygisk/DenyList if MagiskSU)
    final hasMagiskSuRoot = status.isRooted;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.settings, isDark),
            if (_isLoading)
              const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              )
            else
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(4),
                  children: [
                    // Magisk Settings Section - Only show if MagiskSU root
                    if (hasMagiskSuRoot) ...[
                      _buildSectionHeader(context, 'Magisk Settings', isDark),
                      _buildSettingTile(
                        context,
                        localizations.zygisk,
                        localizations.zygiskDesc,
                        Icons.security,
                        widgetColor,
                        _isZygiskEnabled,
                        _toggleZygisk,
                        isDark,
                      ),
                      // DenyList page navigation - single item for both toggle and navigation
                      _buildNavigationTile(
                        context,
                        localizations.denyList,
                        localizations.denyListDesc,
                        Icons.shield,
                        widgetColor,
                        isDark,
                        onTap: _isZygiskEnabled 
                            ? () => Navigator.push(context, FlipPageRoute(page: const DenyListPage()))
                            : () => _showZygiskRequiredDialog(),
                      ),
                      const SizedBox(height: 8),
                      const Divider(height: 1, thickness: 1),
                      const SizedBox(height: 8),
                    ],
                    
                    // App Settings Section
                    _buildSectionHeader(context, 'App Settings', isDark),
                    _buildNavigationTile(
                      context,
                      localizations.darkMode,
                      isDark ? localizations.enabled : localizations.disabled,
                      Icons.nightlight_round,
                      widgetColor,
                      isDark,
                      trailing: Switch(
                        value: isDark,
                        onChanged: (v) => ref.read(themeProvider.notifier).state = v,
                        activeColor: widgetColor,
                      ),
                    ),
                    _buildNavigationTile(
                      context,
                      localizations.theme,
                      AppTheme.tileColorNames[tileColorIndex],
                      Icons.palette,
                      widgetColor,
                      isDark,
                      onTap: () => Navigator.push(context, FlipPageRoute(page: const ThemePage())),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
  
  void _showZygiskRequiredDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Zygisk Required'),
        content: Text('DenyList requires Zygisk to be enabled first. Please enable Zygisk before using DenyList.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('OK'),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext context, String title, bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Text(
        title,
        style: GoogleFonts.poppins(
          fontWeight: FontWeight.w600,
          fontSize: 14,
          color: AppTheme.getFont(isDark).withValues(alpha: 0.6),
        ),
      ),
    );
  }

  Widget _buildSettingTile(
    BuildContext context,
    String title,
    String subtitle,
    IconData icon,
    Color widgetColor,
    bool value,
    Function(bool) onToggle,
    bool isDark, {
    bool enabled = true,
  }) {
    return Opacity(
      opacity: enabled ? 1.0 : 0.5,
      child: GestureDetector(
        onTap: enabled
            ? () {
                onToggle(!value);
              }
            : null,
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
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
              Switch(
                value: value,
                onChanged: enabled ? onToggle : null,
                activeColor: widgetColor,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNavigationTile(
    BuildContext context,
    String title,
    String? subtitle,
    IconData icon,
    Color widgetColor,
    bool isDark, {
    Widget? trailing,
    VoidCallback? onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
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
                  if (subtitle != null)
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
            if (trailing != null)
              trailing
            else if (onTap != null)
              Icon(
                Icons.chevron_right,
                color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
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

class ModulesPage extends ConsumerStatefulWidget {
  const ModulesPage({super.key});

  @override
  ConsumerState<ModulesPage> createState() => _ModulesPageState();
}

class _ModulesPageState extends ConsumerState<ModulesPage> {
  final Map<String, bool> _moduleDetailsLoaded = {};
  Set<String> _operatingModules = {}; // Track modules being operated on
  
  Future<void> _toggleModule(Module module, bool enabled) async {
    // Optimistic update - immediately update UI
    ref.read(modulesProvider.notifier).toggleModule(module.name, enabled);
    
    // Execute in background
    try {
      await AndroidDataService.toggleModule(module.path, enabled);
    } catch (e) {
      // Revert on error
      ref.read(modulesProvider.notifier).toggleModule(module.name, !enabled);
    }
  }
  
  Future<void> _removeModule(Module module) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Remove Module'),
        content: Text('Are you sure you want to remove "${module.name}"?\n\nThis will delete the module after reboot.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: Text('Remove'),
          ),
        ],
      ),
    );
    
    if (confirmed == true) {
      final success = await AndroidDataService.removeModule(module.path);
      if (success && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${module.name} will be removed after reboot')),
        );
        // Refresh module list
        ref.refresh(modulesProvider);
      }
    }
  }
  
  Future<void> _executeAction(Module module) async {
    // Show loading dialog
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        content: Row(
          children: [
            CircularProgressIndicator(),
            SizedBox(width: 16),
            Text('Executing action...'),
          ],
        ),
      ),
    );
    
    final output = await AndroidDataService.executeModuleAction(module.path);
    
    if (mounted) {
      Navigator.pop(context); // Close loading dialog
      
      // Show result dialog
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: Row(
            children: [
              Icon(output != null ? Icons.check_circle : Icons.error,
                  color: output != null ? Colors.green : Colors.red),
              SizedBox(width: 8),
              Text('Action Result'),
            ],
          ),
          content: SingleChildScrollView(
            child: Text(output ?? 'Failed to execute action script'),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text('OK'),
            ),
          ],
        ),
      );
    }
  }
  
  Future<void> _openWebUI(Module module) async {
    if (module.webUIUrl == null) return;
    
    // Open WebUI in our custom WebView page with flip animation
    Navigator.push(
      context,
      FlipPageRoute(
        page: ModuleWebUIPage(
          module: module,
          webUIUrl: module.webUIUrl!,
        ),
      ),
    );
  }
  
  Future<void> _loadModuleDetails(Module module) async {
    if (_moduleDetailsLoaded[module.path] == true) return;
    
    final details = await AndroidDataService.getModuleDetails(module.path);
    if (details.isNotEmpty) {
      _moduleDetailsLoaded[module.path] = true;
      // The provider should be updated to include these details
    }
  }

  @override
  Widget build(BuildContext context) {
    final modules = ref.watch(modulesProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(3, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.modules, isDark, widgetColor),
            Expanded(
              child: modules.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.extension_off,
                            size: 64,
                            color: AppTheme.getFont(isDark).withValues(alpha: 0.3),
                          ),
                          const SizedBox(height: 16),
                          Text(
                            localizations.noModules,
                            style: GoogleFonts.poppins(
                              fontSize: 16,
                              color: AppTheme.getFont(isDark).withValues(alpha: 0.6),
                            ),
                          ),
                        ],
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: () async {
                        _moduleDetailsLoaded.clear();
                        ref.refresh(modulesProvider);
                      },
                      child: ListView.builder(
                        padding: const EdgeInsets.all(4),
                        itemCount: modules.length,
                        itemBuilder: (context, index) {
                          final module = modules[index];
                          return _buildModuleTile(module, widgetColor, isDark);
                        },
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildModuleTile(Module module, Color widgetColor, bool isDark) {
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      color: AppTheme.getListItem(isDark),
      child: ExpansionTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: module.isEnabled 
                ? widgetColor.withValues(alpha: 0.2)
                : AppTheme.getListItem(isDark).withValues(alpha: 0.5),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(
            Icons.extension,
            color: module.isEnabled ? widgetColor : widgetColor.withValues(alpha: 0.5),
          ),
        ),
        title: Row(
          children: [
            Expanded(
              child: Text(
                module.name,
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  color: AppTheme.getListItemFont(isDark),
                ),
              ),
            ),
            if (!module.isEnabled)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.red.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  'DISABLED',
                  style: GoogleFonts.poppins(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    color: Colors.red,
                  ),
                ),
              ),
          ],
        ),
        subtitle: Text(
          '${module.version} • ${module.author}',
          style: GoogleFonts.poppins(
            fontSize: 12,
            color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
          ),
        ),
        trailing: Switch(
          value: module.isEnabled,
          onChanged: (value) => _toggleModule(module, value),
          activeColor: widgetColor,
        ),
        children: [
          Container(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Description
                if (module.description.isNotEmpty) ...[
                  Text(
                    module.description,
                    style: GoogleFonts.poppins(
                      fontSize: 14,
                      color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.8),
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                // Action buttons - using Row with spacing for alignment
                Row(
                  children: [
                    // Web UI button
                    if (module.hasWebUI)
                      Expanded(
                        child: _buildModuleButton(
                          icon: Icons.web,
                          label: 'Web UI',
                          color: widgetColor,
                          onTap: () => _openWebUI(module),
                        ),
                      ),
                    if (module.hasWebUI && (module.hasActionScript || true))
                      const SizedBox(width: 8),
                    // Action script button
                    if (module.hasActionScript)
                      Expanded(
                        child: _buildModuleButton(
                          icon: Icons.play_arrow,
                          label: 'Run Action',
                          color: widgetColor.withValues(alpha: 0.85),
                          onTap: () => _executeAction(module),
                        ),
                      ),
                    if (module.hasActionScript)
                      const SizedBox(width: 8),
                    // Remove button - always shown
                    Expanded(
                      child: _buildModuleButton(
                        icon: Icons.delete_outline,
                        label: 'Remove',
                        color: Colors.red.withValues(alpha: 0.9),
                        onTap: () => _removeModule(module),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                // Path info
                Text(
                  'Path: ${module.path}',
                  style: GoogleFonts.poppins(
                    fontSize: 12,
                    color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark, Color widgetColor) {
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
          // Load icon button for module installation
          GestureDetector(
            onTap: () async {
              final filePath = await AndroidDataService.pickFile();
              if (filePath != null && context.mounted) {
                _performInstallModule(context, filePath);
              }
            },
            child: Container(
              padding: const EdgeInsets.all(8),
              child: Icon(Icons.system_update_alt,
                  color: widgetColor, size: 24),
            ),
          ),
        ],
      ),
    );
  }
  
  void _performInstallModule(BuildContext context, String zipPath) {
    final localizations = AppLocalizations.of(context)!;
    Navigator.push(
      context,
      FlipPageRoute(
        page: FlashLogsPage(
          title: localizations.installModule,
          onExecute: () async {
            try {
              final result = await AndroidDataService.installModule(zipPath);
              return result;
            } catch (e) {
              return false;
            }
          },
        ),
      ),
    );
  }
  
  // Helper method to build consistent module buttons
  Widget _buildModuleButton({
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback onTap,
  }) {
    return Material(
      color: color,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 18, color: Colors.white),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  label,
                  style: GoogleFonts.poppins(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AppsPage extends ConsumerStatefulWidget {
  const AppsPage({super.key});

  @override
  ConsumerState<AppsPage> createState() => _AppsPageState();
}

class _AppsPageState extends ConsumerState<AppsPage> with AutomaticKeepAliveClientMixin, RouteAware {
  bool _showOnlyRootApps = false;
  String _searchQuery = '';
  List<AppInfo> _cachedApps = [];
  List<AppInfo> _filteredApps = [];
  bool _isInitialized = false;
  List<String> _packageOrder = []; // Track package order persistently
  
  @override
  bool get wantKeepAlive => true;
  
  @override
  void initState() {
    super.initState();
    AndroidDataService.initialize();
  }
  
  @override
  void didPop() {
    // Flush pending changes when leaving the page
    ref.read(appsProvider.notifier).flushPendingChanges();
    super.didPop();
  }
  
  @override
  void didPopNext() {
    // Called when coming back to this page from another route
    // Refresh to get latest data
    ref.read(appsProvider.notifier).refresh();
  }
  
  void _updateFilteredApps() {
    _filteredApps = _cachedApps.where((app) {
      if (_searchQuery.isNotEmpty) {
        final query = _searchQuery.toLowerCase();
        if (!app.name.toLowerCase().contains(query) &&
            !app.packageName.toLowerCase().contains(query)) {
          return false;
        }
      }
      if (_showOnlyRootApps && !app.hasRootAccess) {
        return false;
      }
      return true;
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    
    final apps = ref.watch(appsProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(4, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;

    // Check if this is a completely new list (app added/removed) vs just state update
    final currentPackages = apps.map((a) => a.packageName).toSet();
    final cachedPackages = _packageOrder.toSet();
    final isStructureChange = _cachedApps.isEmpty || 
        apps.length != _cachedApps.length ||
        !currentPackages.containsAll(cachedPackages) ||
        !cachedPackages.containsAll(currentPackages);
    
    // Update cache
    if (isStructureChange) {
      // Full refresh - rebuild and re-sort
      _cachedApps = List.from(apps);
      _cachedApps.sort((a, b) {
        if (a.hasRootAccess && !b.hasRootAccess) return -1;
        if (!a.hasRootAccess && b.hasRootAccess) return 1;
        return a.name.compareTo(b.name);
      });
      // Store the new order
      _packageOrder = _cachedApps.map((a) => a.packageName).toList();
      _updateFilteredApps();
    } else {
      // Just state update - maintain existing order, only update hasRootAccess values
      for (int i = 0; i < _cachedApps.length; i++) {
        final cachedApp = _cachedApps[i];
        final updatedApp = apps.firstWhere(
          (a) => a.packageName == cachedApp.packageName,
          orElse: () => cachedApp,
        );
        _cachedApps[i] = AppInfo(
          name: cachedApp.name,
          packageName: cachedApp.packageName,
          isActive: cachedApp.isActive,
          hasRootAccess: updatedApp.hasRootAccess,
        );
      }
      _updateFilteredApps();
    }

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations, isDark, widgetColor),
            Expanded(
              child: _buildAppsList(isDark, widgetColor, localizations),
            ),
          ],
        ),
      ),
    );
  }
  
  bool _listEquals(List<AppInfo> a, List<AppInfo> b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i].packageName != b[i].packageName || 
          a[i].hasRootAccess != b[i].hasRootAccess) {
        return false;
      }
    }
    return true;
  }
  
  Widget _buildHeader(BuildContext context, AppLocalizations localizations, bool isDark, Color widgetColor) {
    final rootAppsCount = _cachedApps.where((app) => app.hasRootAccess).length;
    
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
          color: AppTheme.getTile(isDark),
          child: Row(
            children: [
              GestureDetector(
                onTap: () {
                  // Flush pending changes before navigating back
                  ref.read(appsProvider.notifier).flushPendingChanges();
                  Navigator.pop(context);
                },
                child: Padding(
                  padding: const EdgeInsets.all(8),
                  child: Icon(Icons.chevron_left, color: AppTheme.getFont(isDark), size: 28),
                ),
              ),
              Expanded(
                child: Text(
                  localizations.apps,
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w900,
                    fontSize: 20,
                    color: AppTheme.getFont(isDark),
                  ),
                ),
              ),
              GestureDetector(
                onTap: () => setState(() => _showOnlyRootApps = !_showOnlyRootApps),
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: _showOnlyRootApps ? widgetColor.withValues(alpha: 0.2) : null,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      Icon(Icons.filter_list,
                        color: _showOnlyRootApps 
                            ? widgetColor 
                            : AppTheme.getFont(isDark).withValues(alpha: 0.7),
                        size: 24),
                      if (_showOnlyRootApps)
                        Positioned(
                          right: -4, top: -4,
                          child: Container(
                            padding: const EdgeInsets.all(3),
                            decoration: BoxDecoration(
                              color: widgetColor,
                              shape: BoxShape.circle,
                            ),
                            child: Text('$rootAppsCount',
                              style: GoogleFonts.poppins(
                                color: Colors.white, fontSize: 8, fontWeight: FontWeight.bold)),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.all(8),
          color: AppTheme.getTile(isDark),
          child: TextField(
            onChanged: (value) {
              _searchQuery = value;
              _updateFilteredApps();
              setState(() {});
            },
            decoration: InputDecoration(
              hintText: 'Search apps...',
              hintStyle: GoogleFonts.poppins(
                color: AppTheme.getFont(isDark).withValues(alpha: 0.5),
              ),
              prefixIcon: Icon(Icons.search, color: widgetColor),
              filled: true,
              fillColor: AppTheme.getListItem(isDark),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: BorderSide.none,
              ),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            ),
            style: GoogleFonts.poppins(color: AppTheme.getListItemFont(isDark)),
          ),
        ),
      ],
    );
  }
  
  Widget _buildAppsList(bool isDark, Color widgetColor, AppLocalizations localizations) {
    if (_filteredApps.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(_showOnlyRootApps ? Icons.security : Icons.apps,
              size: 64, color: AppTheme.getFont(isDark).withValues(alpha: 0.3)),
            const SizedBox(height: 16),
            Text(
              _showOnlyRootApps 
                  ? 'No apps with root access'
                  : (_searchQuery.isNotEmpty 
                      ? 'No apps found matching "$_searchQuery"'
                      : localizations.noApps),
              style: GoogleFonts.poppins(
                fontSize: 16,
                color: AppTheme.getFont(isDark).withValues(alpha: 0.6),
              ),
              textAlign: TextAlign.center,
            ),
            if (_showOnlyRootApps || _searchQuery.isNotEmpty) ...[
              const SizedBox(height: 16),
              TextButton(
                onPressed: () => setState(() {
                  _showOnlyRootApps = false;
                  _searchQuery = '';
                  _updateFilteredApps();
                }),
                child: Text('Show all apps',
                  style: GoogleFonts.poppins(color: widgetColor, fontWeight: FontWeight.w600)),
              ),
            ],
          ],
        ),
      );
    }
    
    return RefreshIndicator(
      onRefresh: () async {
        _cachedApps = [];
        await ref.read(appsProvider.notifier).refreshApps();
      },
      child: ListView.builder(
        padding: const EdgeInsets.all(4),
        itemCount: _filteredApps.length,
        cacheExtent: 500, // Pre-cache items for smoother scrolling
        itemBuilder: (context, index) {
          final app = _filteredApps[index];
          return _AppTile(
            key: ValueKey(app.packageName),
            app: app,
            widgetColor: widgetColor,
            isDark: isDark,
            onToggle: (value) async {
              await ref.read(appsProvider.notifier).toggleRootAccessViaScript(app.packageName, value);
            },
          );
        },
      ),
    );
  }
}

// Separate widget for each app tile to minimize rebuilds
class _AppTile extends StatelessWidget {
  final AppInfo app;
  final Color widgetColor;
  final bool isDark;
  final Function(bool) onToggle;

  const _AppTile({
    super.key,
    required this.app,
    required this.widgetColor,
    required this.isDark,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      color: AppTheme.getListItem(isDark),
      child: ListTile(
        leading: Container(
          width: 40, height: 40,
          decoration: BoxDecoration(
            color: app.hasRootAccess 
                ? widgetColor.withValues(alpha: 0.2)
                : AppTheme.getListItem(isDark).withValues(alpha: 0.5),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(
            app.hasRootAccess ? Icons.verified_user : Icons.security,
            color: app.hasRootAccess ? widgetColor : widgetColor.withValues(alpha: 0.5),
          ),
        ),
        title: Row(
          children: [
            Expanded(
              child: Text(app.name,
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w900,
                  color: AppTheme.getListItemFont(isDark),
                ),
              ),
            ),
            if (app.hasRootAccess)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: widgetColor.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text('ROOT',
                  style: GoogleFonts.poppins(
                    fontSize: 10, fontWeight: FontWeight.w700, color: widgetColor)),
              ),
          ],
        ),
        subtitle: Text(app.packageName,
          style: GoogleFonts.poppins(
            fontSize: 12,
            color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
          ),
        ),
        trailing: Switch(
          value: app.hasRootAccess,
          onChanged: onToggle,
          activeColor: widgetColor,
        ),
      ),
    );
  }
}

class LogsPage extends ConsumerStatefulWidget {
  const LogsPage({super.key});

  @override
  ConsumerState<LogsPage> createState() => _LogsPageState();
}

class _LogsPageState extends ConsumerState<LogsPage> {
  Future<void> _saveLogsToFile() async {
    try {
      final logsAsync = ref.read(logsProvider);
      if (logsAsync.value != null) {
        final logs = logsAsync.value!;
        final logContent = logs.join('\n');
        
        // Get current date and time for filename
        final now = DateTime.now();
        final timestamp = '${now.year}${now.month.toString().padLeft(2, '0')}${now.day.toString().padLeft(2, '0')}_${now.hour.toString().padLeft(2, '0')}${now.minute.toString().padLeft(2, '0')}${now.second.toString().padLeft(2, '0')}';
        final filename = 'MagisKube_logs_$timestamp.log';
        
        // Save to Downloads directory
        final result = await AndroidDataService.saveLogToFile(logContent, filename);
        if (result) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Logs saved successfully!')),
          );
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Failed to save logs!')),
          );
        }
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error saving logs: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final logsAsync = ref.watch(logsProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(2, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.logs, isDark, widgetColor, localizations),
            Expanded(
              child: logsAsync.when(
                data: (logs) => RefreshIndicator(
                  onRefresh: () => ref.read(logsProvider.notifier).refresh(),
                  child: logs.isEmpty 
                    ? ListView(
                        children: [
                          SizedBox(height: MediaQuery.of(context).size.height * 0.3),
                          Center(
                            child: Text(
                              'No logs available',
                              style: GoogleFonts.poppins(
                                fontSize: 16,
                                color: AppTheme.getFont(isDark).withValues(alpha: 0.6),
                              ),
                            ),
                          ),
                        ],
                      )
                    : ListView.builder(
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
                ),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, stack) => Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text('Error: $error', style: GoogleFonts.poppins(color: Colors.red)),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => ref.read(logsProvider.notifier).refresh(),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark, Color widgetColor, AppLocalizations localizations) {
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
          GestureDetector(
            onTap: _saveLogsToFile,
            child: Container(
              padding: const EdgeInsets.all(8),
              child: Icon(Icons.save, color: AppTheme.getFont(isDark), size: 24),
            ),
          ),
          const SizedBox(width: 8),
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
    final localizations = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.contributors, isDark),
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
                      trailing: contributor.github != null && contributor.github != 'none'
                          ? Icon(Icons.open_in_new,
                              color: AppTheme.getListItemFont(isDark)
                                  .withValues(alpha: 0.6))
                          : null,
                      onTap: () {
                        if (contributor.github != null && contributor.github != 'none') {
                          _openGithub(contributor.github!);
                        } else {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Not valid GitHub Link')),
                          );
                        }
                      },
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

  Future<void> _openGithub(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
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
    final localizations = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.theme, isDark),
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
                              color: _getTileColor(index, isDark),
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

  Color _getTileColor(int index, bool isDark) {
    if (index == AppTheme.tileColorNames.length - 1) {
      // Monet Theme - use a special color to indicate it's dynamic
      return isDark ? Colors.grey[700]! : Colors.grey[300]!;
    }
    if (index == 0) {
      return isDark ? const Color(0xFF009688) : const Color(0xFF4DB6AC);
    }
    return AppTheme.tileColors[index];
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

// Activity info model
class ActivityInfo {
  final String name;
  final bool isInDenyList;
  
  ActivityInfo({required this.name, required this.isInDenyList});
}

/// DenyList Page - Manage apps that should hide root
/// Uses cached state from provider with async sync on refresh
/// Apps in DenyList are sorted to top like Apps page
class DenyListPage extends ConsumerStatefulWidget {
  const DenyListPage({super.key});

  @override
  ConsumerState<DenyListPage> createState() => _DenyListPageState();
}

class _DenyListPageState extends ConsumerState<DenyListPage> with RouteAware {
  String _searchQuery = '';
  Map<String, bool> _expandedApps = {}; // Track which apps are expanded
  Map<String, List<ActivityInfo>> _appActivities = {}; // Cached activities
  List<AppInfo> _cachedApps = []; // Local cache for sorting
  Map<String, bool> _pendingChanges = {}; // Pending denylist changes (not yet flushed)
  Set<String> _confirmedDenyListApps = {}; // Confirmed state from magisk.db (used for labels)
  
  @override
  void dispose() {
    // Flush pending changes when leaving the page
    _flushPendingChanges();
    super.dispose();
  }
  
  Future<void> _flushPendingChanges() async {
    if (_pendingChanges.isEmpty) return;
    
    // Execute all pending changes to magisk.db
    for (final entry in _pendingChanges.entries) {
      final packageName = entry.key;
      final shouldAdd = entry.value;
      
      try {
        if (shouldAdd) {
          await AndroidDataService.addToDenyList(packageName);
          _confirmedDenyListApps.add(packageName);
        } else {
          await AndroidDataService.removeFromDenyList(packageName);
          _confirmedDenyListApps.remove(packageName);
        }
      } catch (e) {
        debugPrint('Error flushing denylist change for $packageName: $e');
      }
    }
    
    // Clear pending changes
    _pendingChanges = {};
    
    // Update provider state
    final notifier = ref.read(denyListStateProvider.notifier);
    await notifier.refresh();
  }
  
  void _toggleAppInDenyList(String packageName) {
    // Get current switch state (considering pending changes)
    final currentSwitchState = _getSwitchState(packageName);
    final newState = !currentSwitchState;
    
    // Update or remove pending change
    // If the new state matches the confirmed state, remove from pending
    // Otherwise, add to pending
    final confirmedState = _confirmedDenyListApps.contains(packageName);
    
    if (newState == confirmedState) {
      // New state matches confirmed state - remove from pending if exists
      _pendingChanges.remove(packageName);
    } else {
      // New state differs from confirmed - track as pending
      _pendingChanges[packageName] = newState;
    }
    
    // Also update all activities for this app
    final activities = _appActivities[packageName] ?? [];
    if (activities.isNotEmpty) {
      for (final activity in activities) {
        final fullActivityName = '$packageName/${activity.name}';
        if (newState == confirmedState) {
          _pendingChanges.remove(fullActivityName);
        } else {
          _pendingChanges[fullActivityName] = newState;
        }
      }
      // Update cached activities UI state
      setState(() {
        _appActivities[packageName] = activities.map((a) => 
            ActivityInfo(name: a.name, isInDenyList: newState)).toList();
      });
    }
    
    // Trigger rebuild to update switch state
    setState(() {});
  }
  
  /// Get the switch state for an app (considers pending changes)
  /// Switch is ON if app OR any of its activities are in DenyList
  bool _getSwitchState(String packageName) {
    // Check pending changes first
    if (_pendingChanges.containsKey(packageName)) {
      return _pendingChanges[packageName]!;
    }
    
    // Check if app is in confirmed list
    if (_confirmedDenyListApps.contains(packageName)) {
      return true;
    }
    
    // Check if any activity has pending change to true
    final activities = _appActivities[packageName] ?? [];
    for (final activity in activities) {
      final fullActivityName = '$packageName/${activity.name}';
      if (_pendingChanges[fullActivityName] == true) {
        return true;
      }
    }
    
    return false;
  }
  
  /// Get the label visibility (based on confirmed magisk.db state OR pending enable)
  /// Shows label when app OR any activity is in DenyList
  bool _shouldShowDenyListLabel(String packageName) {
    // Check confirmed state
    if (_confirmedDenyListApps.contains(packageName)) {
      return true;
    }
    
    // Check if any activity is confirmed in DenyList
    final denyListState = ref.read(denyListStateProvider);
    for (final activity in denyListState.activities) {
      if (activity.startsWith('$packageName/')) {
        return true;
      }
    }
    
    return false;
  }
  
  /// Get the icon state - shows "visibility_off" with background when in DenyList
  /// This reflects the overall DenyList status for the app
  bool _isAppInDenyList(String packageName) {
    return _getSwitchState(packageName);
  }
  
  List<AppInfo> _sortApps(List<AppInfo> apps, Set<String> denyListApps) {
    final sorted = List<AppInfo>.from(apps);
    sorted.sort((a, b) {
      final aInDenyList = denyListApps.contains(a.packageName);
      final bInDenyList = denyListApps.contains(b.packageName);
      
      // Apps in DenyList first
      if (aInDenyList && !bInDenyList) return -1;
      if (!aInDenyList && bInDenyList) return 1;
      
      // Then alphabetically
      return a.name.compareTo(b.name);
    });
    return sorted;
  }
  
  Future<void> _loadAppActivities(String packageName) async {
    if (_appActivities.containsKey(packageName)) return;
    
    try {
      final activities = await AndroidDataService.getAppActivities(packageName);
      final denyListState = ref.read(denyListStateProvider);
      
      final activityInfos = activities.map((activity) {
        final fullActivityName = '$packageName/$activity';
        return ActivityInfo(
          name: activity,
          isInDenyList: denyListState.activities.contains(fullActivityName),
        );
      }).toList();
      
      setState(() {
        _appActivities[packageName] = activityInfos;
      });
    } catch (e) {
      setState(() {
        _appActivities[packageName] = [];
      });
    }
  }
  
  void _toggleActivityInDenyList(String packageName, String activityName) {
    final fullActivityName = '$packageName/$activityName';
    final denyListState = ref.read(denyListStateProvider);
    final isInList = denyListState.activities.contains(fullActivityName);
    
    // Update local state
    final notifier = ref.read(denyListStateProvider.notifier);
    notifier.toggleActivity(fullActivityName, !isInList);
    
    // Update cached activities
    final activities = _appActivities[packageName];
    if (activities != null) {
      final index = activities.indexWhere((a) => a.name == activityName);
      if (index >= 0) {
        setState(() {
          _appActivities[packageName]![index] = ActivityInfo(
            name: activityName, 
            isInDenyList: !isInList,
          );
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final apps = ref.watch(appsProvider);
    final denyListState = ref.watch(denyListStateProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(1, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;
    
    // Initialize confirmed denylist apps from provider (only on first load or after refresh)
    if (_confirmedDenyListApps.isEmpty && !denyListState.isLoading) {
      _confirmedDenyListApps = Set<String>.from(denyListState.apps);
    }
    
    // Sort apps based on CONFIRMED state (not pending)
    if (_cachedApps.isEmpty || 
        _cachedApps.length != apps.length ||
        !_listEquals(_cachedApps, apps)) {
      _cachedApps = _sortApps(apps, _confirmedDenyListApps);
    } else {
      // Just update sort order based on confirmed denylist
      _cachedApps = _sortApps(_cachedApps, _confirmedDenyListApps);
    }
    
    // Filter apps based on search
    final filteredApps = _cachedApps.where((app) {
      if (_searchQuery.isNotEmpty) {
        final query = _searchQuery.toLowerCase();
        return app.name.toLowerCase().contains(query) ||
               app.packageName.toLowerCase().contains(query);
      }
      return true;
    }).toList();

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.denyList, isDark, widgetColor),
            if (denyListState.isLoading)
              const Expanded(child: Center(child: CircularProgressIndicator()))
            else
              Expanded(
                child: Column(
                  children: [
                    // Enable/Disable switch
                    Container(
                      margin: const EdgeInsets.all(8),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppTheme.getListItem(isDark),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.shield, color: widgetColor),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  localizations.enforceDenyList,
                                  style: GoogleFonts.poppins(
                                    fontWeight: FontWeight.w900,
                                    fontSize: 16,
                                    color: AppTheme.getListItemFont(isDark),
                                  ),
                                ),
                                Text(
                                  localizations.enforceDenyListDesc,
                                  style: GoogleFonts.poppins(
                                    fontSize: 12,
                                    color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Switch(
                            value: denyListState.isEnabled,
                            onChanged: (enabled) async {
                              final notifier = ref.read(denyListStateProvider.notifier);
                              await notifier.setEnabled(enabled);
                            },
                            activeColor: widgetColor,
                          ),
                        ],
                      ),
                    ),
                    // Search bar
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                      child: TextField(
                        onChanged: (value) => setState(() => _searchQuery = value),
                        decoration: InputDecoration(
                          hintText: 'Search apps...',
                          hintStyle: GoogleFonts.poppins(
                            color: AppTheme.getFont(isDark).withValues(alpha: 0.5),
                          ),
                          prefixIcon: Icon(Icons.search, color: widgetColor),
                          filled: true,
                          fillColor: AppTheme.getListItem(isDark),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(8),
                            borderSide: BorderSide.none,
                          ),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                        ),
                        style: GoogleFonts.poppins(color: AppTheme.getListItemFont(isDark)),
                      ),
                    ),
                    const SizedBox(height: 8),
                    // Apps list
                    Expanded(
                      child: RefreshIndicator(
                        onRefresh: () async {
                          // Flush pending changes first
                          await _flushPendingChanges();
                          // Then refresh from magisk.db
                          await ref.read(denyListStateProvider.notifier).refresh();
                        },
                        child: ListView.builder(
                          padding: const EdgeInsets.all(4),
                          itemCount: filteredApps.length,
                          itemBuilder: (context, index) {
                            final app = filteredApps[index];
                            // Switch state considers pending changes
                            final switchState = _getSwitchState(app.packageName);
                            // Label only shows for confirmed denylist apps
                            final showLabel = _shouldShowDenyListLabel(app.packageName);
                            final isExpanded = _expandedApps[app.packageName] ?? false;
                            
                            return _DenyListAppTile(
                              key: ValueKey(app.packageName),
                              app: app,
                              switchState: switchState,
                              showDenyListLabel: showLabel,
                              isExpanded: isExpanded,
                              widgetColor: widgetColor,
                              isDark: isDark,
                              activities: _appActivities[app.packageName],
                              onToggle: () => _toggleAppInDenyList(app.packageName),
                              onExpand: () async {
                                setState(() {
                                  _expandedApps[app.packageName] = !isExpanded;
                                });
                                if (!isExpanded) {
                                  await _loadAppActivities(app.packageName);
                                }
                              },
                              onActivityToggle: (activityName) => 
                                  _toggleActivityInDenyList(app.packageName, activityName),
                              localizations: localizations,
                            );
                          },
                        ),
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
  
  bool _listEquals(List<AppInfo> a, List<AppInfo> b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i].packageName != b[i].packageName) return false;
    }
    return true;
  }
  
  Widget _buildHeader(BuildContext context, String title, bool isDark, Color widgetColor) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          GestureDetector(
            onTap: () => Navigator.pop(context),
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: Icon(Icons.chevron_left, color: AppTheme.getFont(isDark), size: 28),
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
          // DenyList count badge
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: widgetColor.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              '${ref.watch(denyListStateProvider).apps.length}',
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w700,
                fontSize: 12,
                color: widgetColor,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Separate widget for each DenyList app tile to minimize rebuilds
class _DenyListAppTile extends StatelessWidget {
  final AppInfo app;
  final bool switchState; // Current switch state (considers pending changes)
  final bool showDenyListLabel; // Show label only for confirmed denylist apps
  final bool isExpanded;
  final Color widgetColor;
  final bool isDark;
  final List<ActivityInfo>? activities;
  final VoidCallback onToggle;
  final VoidCallback onExpand;
  final Function(String) onActivityToggle;
  final AppLocalizations localizations;

  const _DenyListAppTile({
    super.key,
    required this.app,
    required this.switchState,
    required this.showDenyListLabel,
    required this.isExpanded,
    required this.widgetColor,
    required this.isDark,
    this.activities,
    required this.onToggle,
    required this.onExpand,
    required this.onActivityToggle,
    required this.localizations,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      color: AppTheme.getListItem(isDark),
      child: Column(
        children: [
          // Main tile
          ListTile(
            leading: Container(
              width: 40, height: 40,
              decoration: BoxDecoration(
                color: switchState 
                    ? widgetColor.withValues(alpha: 0.2)
                    : AppTheme.getListItem(isDark).withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(
                switchState ? Icons.visibility_off : Icons.visibility,
                color: switchState ? widgetColor : widgetColor.withValues(alpha: 0.5),
              ),
            ),
            title: Row(
              children: [
                Expanded(
                  child: Text(
                    app.name,
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      color: AppTheme.getListItemFont(isDark),
                    ),
                  ),
                ),
                // Label only shows for CONFIRMED denylist apps (from magisk.db)
                if (showDenyListLabel)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: widgetColor.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      'DENYLIST',
                      style: GoogleFonts.poppins(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: widgetColor,
                      ),
                    ),
                  ),
              ],
            ),
            subtitle: Text(
              app.packageName,
              style: GoogleFonts.poppins(
                fontSize: 12,
                color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
              ),
            ),
            trailing: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Expand button
                GestureDetector(
                  onTap: onExpand,
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Icon(
                      isExpanded ? Icons.expand_less : Icons.expand_more,
                      color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
                    ),
                  ),
                ),
                // Main switch - state considers pending changes
                Switch(
                  value: switchState,
                  onChanged: (_) => onToggle(),
                  activeColor: widgetColor,
                ),
              ],
            ),
          ),
          // Expanded activities list
          if (isExpanded && activities != null)
            Container(
              padding: const EdgeInsets.only(left: 72, right: 16, bottom: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    localizations.activities,
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w600,
                      fontSize: 12,
                      color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
                    ),
                  ),
                  const SizedBox(height: 4),
                  if (activities!.isEmpty)
                    Padding(
                      padding: const EdgeInsets.all(8),
                      child: Text(
                        'Loading activities...',
                        style: GoogleFonts.poppins(
                          fontSize: 11,
                          color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.4),
                        ),
                      ),
                    )
                  else
                    ...activities!.map((activity) {
                      return Container(
                        margin: const EdgeInsets.only(top: 2),
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppTheme.getTile(isDark),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                activity.name,
                                style: GoogleFonts.poppins(
                                  fontSize: 11,
                                  color: AppTheme.getListItemFont(isDark),
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Switch(
                              value: activity.isInDenyList,
                              onChanged: (_) => onActivityToggle(activity.name),
                              activeColor: widgetColor,
                              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            ),
                          ],
                        ),
                      );
                    }).toList(),
                ],
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
    final localizations = AppLocalizations.of(context)!;

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
                Text(localizations.settings,
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
          _buildTile(Icons.nightlight_round, localizations.darkMode,
              isDark ? localizations.enabled : localizations.disabled, isDark,
              trailing: Switch(
                  value: isDark,
                  onChanged: (v) => ref.read(themeProvider.notifier).state = v,
                  activeColor: widgetColor)),
          GestureDetector(
            onTap: onThemeTap,
            child: _buildTile(
                Icons.palette, localizations.theme, 'Change widget color', isDark,
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
