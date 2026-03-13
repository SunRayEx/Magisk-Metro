// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'MagisKube';

  @override
  String get dashboard => 'Dashboard';

  @override
  String get magiskManager => 'Magisk Manager';

  @override
  String get modules => 'Modules';

  @override
  String get apps => 'Apps';

  @override
  String get denyList => 'DenyList';

  @override
  String get logs => 'Logs';

  @override
  String get contributors => 'Contributors';

  @override
  String get settings => 'Settings';

  @override
  String get theme => 'Theme';

  @override
  String get darkMode => 'Dark Mode';

  @override
  String get enabled => 'Enabled';

  @override
  String get disabled => 'Disabled';

  @override
  String get noModules => 'No modules installed';

  @override
  String get noApps => 'No apps with root access';

  @override
  String get installMagisk => 'Install Magisk';

  @override
  String get installMagiskDesc => 'Install, upgrade, or patch Magisk';

  @override
  String get uninstallMagisk => 'Uninstall Magisk';

  @override
  String get uninstallMagiskDesc => 'Remove Magisk from device';

  @override
  String get updateManager => 'Update Manager';

  @override
  String get updateManagerDesc => 'Check for updates';

  @override
  String get magiskInfo => 'Magisk Information';

  @override
  String get version => 'Version';

  @override
  String get root => 'Root';

  @override
  String get zygisk => 'Zygisk';

  @override
  String get ramdisk => 'Ramdisk';

  @override
  String get yes => 'Yes';

  @override
  String get no => 'No';

  @override
  String get loaded => 'Loaded';

  @override
  String get notLoaded => 'Not Loaded';

  @override
  String get autoInstall => 'Auto Install';

  @override
  String get autoInstallDesc => 'Automatically detect and install Magisk';

  @override
  String get patchBootImage => 'Patch Boot Image';

  @override
  String get patchBootImageDesc => 'Select boot image file to patch with Magisk';

  @override
  String get fullUninstall => 'Full Uninstall';

  @override
  String get fullUninstallDesc => 'Remove Magisk and restore images';

  @override
  String get removeOnly => 'Remove Only';

  @override
  String get removeOnlyDesc => 'Remove Magisk without restoring images';

  @override
  String get downloadInstall => 'Download & Install';

  @override
  String get downloadInstallDesc => 'Download latest version and install';

  @override
  String get checkVersion => 'Check Version';

  @override
  String get checkVersionDesc => 'Check latest available version';

  @override
  String get cancel => 'Cancel';

  @override
  String get save => 'Save';

  @override
  String get close => 'Close';

  @override
  String get operationInProgress => 'Operation in progress...';

  @override
  String get operationCompleted => 'Operation completed successfully!';

  @override
  String get operationFailed => 'Operation failed!';

  @override
  String get starting => 'Starting';

  @override
  String get error => 'Error';

  @override
  String get otaSlotSwitch => 'OTA Slot Switch';

  @override
  String get otaSlotSwitchDesc => 'Switch to inactive slot and install Magisk';

  @override
  String get installAddonD => 'Install addon.d Script';

  @override
  String get installAddonDDesc => 'Install OTA recovery script for Magisk';

  @override
  String get restoreMagiskAfterOta => 'Restore Magisk After OTA';

  @override
  String get restoreMagiskAfterOtaDesc => 'Restore Magisk after system OTA update';

  @override
  String get zygiskDesc => 'Enable or disable Zygisk';

  @override
  String get denyListDesc => 'Enable or disable DenyList';

  @override
  String get installModule => 'Install Module';

  @override
  String get installModuleDesc => 'Install a Magisk module from zip file';

  @override
  String get selectModuleZip => 'Select Module Zip';

  @override
  String get selectModuleZipDesc => 'Choose a .zip file to install as Magisk module';

  @override
  String get rootRequired => 'Root access is required for this operation';
}
