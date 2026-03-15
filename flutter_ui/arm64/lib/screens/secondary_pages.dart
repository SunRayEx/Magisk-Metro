import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/dashboard_providers.dart';
import '../models/models.dart';
import '../services/android_data_service.dart';
import 'flash_logs_page.dart';
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
            // Only show auto install if we have root access
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
              onTap: () async {
                Navigator.pop(dialogContext);
                // Use a small delay to ensure dialog is fully closed
                await Future.delayed(const Duration(milliseconds: 100));
                if (!context.mounted) return;
                try {
                  final filePath = await AndroidDataService.pickFile();
                  if (filePath != null && context.mounted) {
                    _performInstallMagisk(context, filePath, isPatchMode: true);
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Error selecting file: $e')),
                    );
                  }
                }
              },
            ),
            // Only show OTA slot switch if we have root access
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

class DenyListPage extends ConsumerStatefulWidget {
  const DenyListPage({super.key});

  @override
  ConsumerState<DenyListPage> createState() => _DenyListPageState();
}

class _DenyListPageState extends ConsumerState<DenyListPage> {
  bool _isZygiskEnabled = false;
  bool _isDenyListEnabled = false;
  bool _isLoading = true;
  Map<String, bool> _expandedApps = {};
  Map<String, List<ActivityInfo>> _appActivities = {};

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    try {
      final zygiskEnabled = await AndroidDataService.isZygiskEnabled();
      final denyListEnabled = await AndroidDataService.isDenyListEnabled();
      setState(() {
        _isZygiskEnabled = zygiskEnabled;
        _isDenyListEnabled = denyListEnabled;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _toggleZygisk(bool enabled) async {
    try {
      final success = await AndroidDataService.setZygiskEnabled(enabled);
      if (success) {
        setState(() {
          _isZygiskEnabled = enabled;
        });
        // Refresh the app list by creating a new AppsNotifier
        ref.refresh(appsProvider);
        // Show restart dialog
        if (enabled) {
          _showRestartDialog();
        }
      }
    } catch (e) {
      // Handle error
    }
  }

  Future<void> _toggleDenyList(bool enabled) async {
    try {
      final success = await AndroidDataService.setDenyListEnabled(enabled);
      if (success) {
        setState(() {
          _isDenyListEnabled = enabled;
          // Clear expanded state and activities when DenyList is disabled
          if (!enabled) {
            _expandedApps.clear();
            _appActivities.clear();
          }
        });
        // Refresh the app list by creating a new AppsNotifier
        ref.refresh(appsProvider);
        // Show restart dialog
        if (enabled) {
          _showRestartDialog();
        }
      }
    } catch (e) {
      // Handle error
    }
  }

  Future<void> _loadAppActivities(String packageName) async {
    if (_appActivities.containsKey(packageName)) {
      return;
    }
    
    try {
      final activities = await AndroidDataService.getAppActivities(packageName);
      final activityInfos = <ActivityInfo>[];
      
      for (final activity in activities) {
        final isInDenyList = await AndroidDataService.isInDenyListActivity(activity);
        activityInfos.add(ActivityInfo(
          name: activity,
          isInDenyList: isInDenyList,
        ));
      }
      
      setState(() {
        _appActivities[packageName] = activityInfos;
      });
    } catch (e) {
      // Handle error
    }
  }

  Future<void> _toggleAppActivity(String packageName, String activityName, bool value) async {
    try {
      bool success;
      if (value) {
        success = await AndroidDataService.addToDenyListActivity(activityName);
      } else {
        success = await AndroidDataService.removeFromDenyListActivity(activityName);
      }
      
      if (success) {
        // Update local state
        final activities = _appActivities[packageName] ?? [];
        for (var i = 0; i < activities.length; i++) {
          if (activities[i].name == activityName) {
            activities[i] = ActivityInfo(
              name: activityName,
              isInDenyList: value,
            );
            break;
          }
        }
        setState(() {
          _appActivities[packageName] = activities;
        });
        // Refresh app list
        ref.refresh(appsProvider);
      }
    } catch (e) {
      // Handle error
    }
  }

  Future<void> _toggleAppAllActivities(String packageName, bool value) async {
    try {
      // Toggle the main app switch
      ref.read(appsProvider.notifier).toggleApp(packageName, !value);
      
      // If enabling all activities, collapse the expansion
      if (value) {
        setState(() {
          _expandedApps[packageName] = false;
        });
      } else {
        // If disabling all activities, load activities for individual control
        await _loadAppActivities(packageName);
        setState(() {
          _expandedApps[packageName] = true;
        });
      }
    } catch (e) {
      // Handle error
    }
  }

  void _toggleAppExpansion(String packageName) {
    if (!_isDenyListEnabled) return;
    
    setState(() {
      final currentExpanded = _expandedApps[packageName] ?? false;
      _expandedApps[packageName] = !currentExpanded;
      if (_expandedApps[packageName]!) {
        _loadAppActivities(packageName);
      }
    });
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
    final apps = ref.watch(appsProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(1, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;

    // Only show apps if DenyList is enabled
    final filteredApps = _isDenyListEnabled ? apps : <AppInfo>[];
    
    // Sort apps: denylist apps (isActive = false) first, then non-denylist apps
    final sortedApps = List<AppInfo>.from(filteredApps)..sort((a, b) {
      if (!a.isActive && b.isActive) return -1; // a is in denylist, b is not
      if (a.isActive && !b.isActive) return 1;  // a is not in denylist, b is
      return a.name.compareTo(b.name); // both in same category, sort by name
    });

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.denyList, isDark),
            if (_isLoading)
              const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              )
            else
              Column(
                children: [
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
                  _buildSettingTile(
                    context,
                    localizations.denyList,
                    localizations.denyListDesc,
                    Icons.visibility_off,
                    widgetColor,
                    _isDenyListEnabled,
                    _toggleDenyList,
                    isDark,
                  ),
                  const Divider(height: 1),
                ],
              ),
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 500),
              transitionBuilder: (Widget child, Animation<double> animation) {
                return ScaleTransition(
                  scale: animation,
                  child: FadeTransition(
                    opacity: animation,
                    child: child,
                  ),
                );
              },
              child: !_isDenyListEnabled
                  ? Expanded(
                      key: const ValueKey('disabled'),
                      child: Center(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Text(
                            'DenyList is disabled. Enable it above to manage apps.',
                            style: GoogleFonts.poppins(
                              fontSize: 14,
                              color: AppTheme.getFont(isDark).withValues(alpha: 0.6),
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ),
                    )
                  : Expanded(
                      key: const ValueKey('enabled'),
                      child: sortedApps.isEmpty
                          ? Center(
                              child: Text(
                                'No apps found',
                                style: GoogleFonts.poppins(
                                  fontSize: 16,
                                  color: AppTheme.getFont(isDark),
                                ),
                              ),
                            )
                          : AnimatedSwitcher(
                              duration: const Duration(milliseconds: 300),
                              transitionBuilder: (Widget child, Animation<double> animation) {
                                return SlideTransition(
                                  position: Tween<Offset>(
                                    begin: const Offset(0, 0.1),
                                    end: Offset.zero,
                                  ).animate(animation),
                                  child: FadeTransition(
                                    opacity: animation,
                                    child: child,
                                  ),
                                );
                              },
                              child: ListView.builder(
                                key: ValueKey(_isDenyListEnabled.toString() + sortedApps.length.toString()),
                                padding: const EdgeInsets.all(4),
                                itemCount: sortedApps.length,
                                itemBuilder: (context, index) {
                                  final app = sortedApps[index];
                                  final isExpanded = _expandedApps[app.packageName] ?? false;
                                  final activities = _appActivities[app.packageName] ?? [];
                                  
                                  // isActive = false means app is in denylist (root hidden)
                                  // isActive = true means app is not in denylist (root visible)
                                  return Column(
                                    key: ValueKey(app.packageName + isExpanded.toString()),
                                    children: [
                                      GestureDetector(
                                        onTap: () => _toggleAppExpansion(app.packageName),
                                        child: Container(
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
                                            trailing: Row(
                                              mainAxisSize: MainAxisSize.min,
                                              children: [
                                                if (!isExpanded)
                                                  Switch(
                                                    value: !app.isActive,
                                                    onChanged: (value) {
                                                      _toggleAppAllActivities(app.packageName, value);
                                                    },
                                                    activeColor: widgetColor,
                                                  ),
                                                Icon(
                                                  isExpanded ? Icons.expand_less : Icons.expand_more,
                                                  color: AppTheme.getListItemFont(isDark).withValues(alpha: 0.6),
                                                ),
                                              ],
                                            ),
                                          ),
                                        ),
                                      ),
                                      if (isExpanded)
                                        AnimatedContainer(
                                          duration: const Duration(milliseconds: 200),
                                          margin: const EdgeInsets.only(left: 16, right: 4, bottom: 4),
                                          child: Column(
                                            children: activities.asMap().entries.map((entry) {
                                              final index = entry.key;
                                              final activity = entry.value;
                                              return AnimatedContainer(
                                                duration: Duration(milliseconds: 100 + (index * 50)),
                                                margin: const EdgeInsets.only(bottom: 2),
                                                color: AppTheme.getListItem(isDark).withValues(alpha: 0.8),
                                                child: ListTile(
                                                  title: Text(
                                                    activity.name.split('.').last,
                                                    style: GoogleFonts.poppins(
                                                      fontSize: 14,
                                                      color: AppTheme.getListItemFont(isDark),
                                                    ),
                                                  ),
                                                  subtitle: Text(
                                                    activity.name,
                                                    style: GoogleFonts.poppins(
                                                      fontSize: 10,
                                                      color: AppTheme.getListItemFont(isDark)
                                                          .withValues(alpha: 0.6),
                                                    ),
                                                  ),
                                                  trailing: Switch(
                                                    value: activity.isInDenyList,
                                                    onChanged: (value) {
                                                      _toggleAppActivity(app.packageName, activity.name, value);
                                                    },
                                                    activeColor: widgetColor,
                                                  ),
                                                ),
                                              );
                                            }).toList(),
                                          ),
                                        ),
                                    ],
                                  );
                                },
                              ),
                            ),
                    ),
            ),
          ],
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
    bool isDark,
  ) {
    return GestureDetector(
      onTap: () {
        onToggle(!value);
      },
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
              onChanged: onToggle,
              activeColor: widgetColor,
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
    final localizations = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, localizations.modules, isDark, widgetColor, ref),
            Expanded(
              child: modules.isEmpty
                  ? Center(
                      child: Text(
                        localizations.noModules,
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

  Widget _buildHeader(BuildContext context, String title, bool isDark, Color widgetColor, WidgetRef ref) {
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
}

class AppsPage extends ConsumerStatefulWidget {
  const AppsPage({super.key});

  @override
  ConsumerState<AppsPage> createState() => _AppsPageState();
}

class _AppsPageState extends ConsumerState<AppsPage> {
  bool _showOnlyRootApps = false;
  String _searchQuery = '';
  
  @override
  void initState() {
    super.initState();
    // Initialize app functions script when page opens
    AndroidDataService.initialize();
  }

  @override
  Widget build(BuildContext context) {
    final apps = ref.watch(appsProvider);
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(4, tileColorIndex, isDark);
    final localizations = AppLocalizations.of(context)!;

    // Filter apps based on search query and filter toggle
    var filteredApps = apps.where((app) {
      if (_searchQuery.isNotEmpty) {
        return app.name.toLowerCase().contains(_searchQuery.toLowerCase()) ||
               app.packageName.toLowerCase().contains(_searchQuery.toLowerCase());
      }
      return true;
    }).toList();
    
    // Apply root apps filter if enabled
    if (_showOnlyRootApps) {
      filteredApps = filteredApps.where((app) => app.hasRootAccess).toList();
    }
    
    // Sort apps: apps with root access first, then alphabetically
    final sortedApps = List<AppInfo>.from(filteredApps)..sort((a, b) {
      if (a.hasRootAccess && !b.hasRootAccess) return -1;
      if (!a.hasRootAccess && b.hasRootAccess) return 1;
      return a.name.compareTo(b.name);
    });

    // Count apps with root access
    final rootAppsCount = apps.where((app) => app.hasRootAccess).length;

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            // Custom header with filter button
            Container(
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
                      localizations.apps,
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: 20,
                        color: AppTheme.getFont(isDark),
                      ),
                    ),
                  ),
                  // Filter button in top right corner
                  Tooltip(
                    message: _showOnlyRootApps ? 'Showing root apps only' : 'Show all apps',
                    child: GestureDetector(
                      onTap: () {
                        setState(() {
                          _showOnlyRootApps = !_showOnlyRootApps;
                        });
                      },
                      child: Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: _showOnlyRootApps
                              ? widgetColor.withValues(alpha: 0.2)
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Stack(
                          children: [
                            Icon(
                              Icons.filter_list,
                              color: _showOnlyRootApps
                                  ? widgetColor
                                  : AppTheme.getFont(isDark).withValues(alpha: 0.7),
                              size: 24,
                            ),
                            if (_showOnlyRootApps)
                              Positioned(
                                right: 0,
                                top: 0,
                                child: Container(
                                  padding: const EdgeInsets.all(2),
                                  decoration: BoxDecoration(
                                    color: widgetColor,
                                    shape: BoxShape.circle,
                                  ),
                                  child: Text(
                                    '$rootAppsCount',
                                    style: GoogleFonts.poppins(
                                      color: Colors.white,
                                      fontSize: 8,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // Search bar
            Container(
              padding: const EdgeInsets.all(8),
              color: AppTheme.getTile(isDark),
              child: TextField(
                onChanged: (value) {
                  setState(() {
                    _searchQuery = value;
                  });
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
                style: GoogleFonts.poppins(
                  color: AppTheme.getListItemFont(isDark),
                ),
              ),
            ),
            // App list
            sortedApps.isEmpty
                ? Expanded(
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            _showOnlyRootApps ? Icons.security : Icons.apps,
                            size: 64,
                            color: AppTheme.getFont(isDark).withValues(alpha: 0.3),
                          ),
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
                              onPressed: () {
                                setState(() {
                                  _showOnlyRootApps = false;
                                  _searchQuery = '';
                                });
                              },
                              child: Text(
                                'Show all apps',
                                style: GoogleFonts.poppins(
                                  color: widgetColor,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  )
                : Expanded(
                    child: RefreshIndicator(
                      onRefresh: () async {
                        await ref.read(appsProvider.notifier).refreshApps();
                      },
                      child: ListView.builder(
                        padding: const EdgeInsets.all(4),
                        itemCount: sortedApps.length,
                        itemBuilder: (context, index) {
                          final app = sortedApps[index];
                          return _buildAppTile(app, widgetColor, isDark, localizations);
                        },
                      ),
                    ),
                  ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppTile(AppInfo app, Color widgetColor, bool isDark, AppLocalizations localizations) {
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      color: AppTheme.getListItem(isDark),
      child: ListTile(
        leading: Container(
          width: 40,
          height: 40,
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
              child: Text(
                app.name,
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
                child: Text(
                  'ROOT',
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
        trailing: Switch(
          value: app.hasRootAccess,
          onChanged: (value) async {
            // Use script-based toggle for root access management
            await ref.read(appsProvider.notifier)
                .toggleRootAccessViaScript(app.packageName, value);
          },
          activeColor: widgetColor,
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
          ElevatedButton.icon(
            onPressed: _saveLogsToFile,
            icon: Icon(Icons.save, size: 18),
            label: Text(localizations.save, style: TextStyle(fontSize: 14)),
            style: ElevatedButton.styleFrom(
              backgroundColor: widgetColor,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
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
