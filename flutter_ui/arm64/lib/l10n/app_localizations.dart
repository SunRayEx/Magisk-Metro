import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale) : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate = _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates = <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('zh')
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'MagisKube'**
  String get appTitle;

  /// No description provided for @dashboard.
  ///
  /// In en, this message translates to:
  /// **'Dashboard'**
  String get dashboard;

  /// No description provided for @magiskManager.
  ///
  /// In en, this message translates to:
  /// **'Magisk Manager'**
  String get magiskManager;

  /// No description provided for @modules.
  ///
  /// In en, this message translates to:
  /// **'Modules'**
  String get modules;

  /// No description provided for @apps.
  ///
  /// In en, this message translates to:
  /// **'Apps'**
  String get apps;

  /// No description provided for @denyList.
  ///
  /// In en, this message translates to:
  /// **'DenyList'**
  String get denyList;

  /// No description provided for @logs.
  ///
  /// In en, this message translates to:
  /// **'Logs'**
  String get logs;

  /// No description provided for @contributors.
  ///
  /// In en, this message translates to:
  /// **'Contributors'**
  String get contributors;

  /// No description provided for @settings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// No description provided for @theme.
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get theme;

  /// No description provided for @darkMode.
  ///
  /// In en, this message translates to:
  /// **'Dark Mode'**
  String get darkMode;

  /// No description provided for @enabled.
  ///
  /// In en, this message translates to:
  /// **'Enabled'**
  String get enabled;

  /// No description provided for @disabled.
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get disabled;

  /// No description provided for @noModules.
  ///
  /// In en, this message translates to:
  /// **'No modules installed'**
  String get noModules;

  /// No description provided for @noApps.
  ///
  /// In en, this message translates to:
  /// **'No apps with root access'**
  String get noApps;

  /// No description provided for @installMagisk.
  ///
  /// In en, this message translates to:
  /// **'Install Magisk'**
  String get installMagisk;

  /// No description provided for @installMagiskDesc.
  ///
  /// In en, this message translates to:
  /// **'Install, upgrade, or patch Magisk'**
  String get installMagiskDesc;

  /// No description provided for @uninstallMagisk.
  ///
  /// In en, this message translates to:
  /// **'Uninstall Magisk'**
  String get uninstallMagisk;

  /// No description provided for @uninstallMagiskDesc.
  ///
  /// In en, this message translates to:
  /// **'Remove Magisk from device'**
  String get uninstallMagiskDesc;

  /// No description provided for @updateManager.
  ///
  /// In en, this message translates to:
  /// **'Update Manager'**
  String get updateManager;

  /// No description provided for @updateManagerDesc.
  ///
  /// In en, this message translates to:
  /// **'Check for updates'**
  String get updateManagerDesc;

  /// No description provided for @magiskInfo.
  ///
  /// In en, this message translates to:
  /// **'Magisk Information'**
  String get magiskInfo;

  /// No description provided for @version.
  ///
  /// In en, this message translates to:
  /// **'Version'**
  String get version;

  /// No description provided for @root.
  ///
  /// In en, this message translates to:
  /// **'Root'**
  String get root;

  /// No description provided for @zygisk.
  ///
  /// In en, this message translates to:
  /// **'Zygisk'**
  String get zygisk;

  /// No description provided for @ramdisk.
  ///
  /// In en, this message translates to:
  /// **'Ramdisk'**
  String get ramdisk;

  /// No description provided for @yes.
  ///
  /// In en, this message translates to:
  /// **'Yes'**
  String get yes;

  /// No description provided for @no.
  ///
  /// In en, this message translates to:
  /// **'No'**
  String get no;

  /// No description provided for @loaded.
  ///
  /// In en, this message translates to:
  /// **'Loaded'**
  String get loaded;

  /// No description provided for @notLoaded.
  ///
  /// In en, this message translates to:
  /// **'Not Loaded'**
  String get notLoaded;

  /// No description provided for @autoInstall.
  ///
  /// In en, this message translates to:
  /// **'Auto Install'**
  String get autoInstall;

  /// No description provided for @autoInstallDesc.
  ///
  /// In en, this message translates to:
  /// **'Automatically detect and install Magisk'**
  String get autoInstallDesc;

  /// No description provided for @patchBootImage.
  ///
  /// In en, this message translates to:
  /// **'Patch Boot Image'**
  String get patchBootImage;

  /// No description provided for @patchBootImageDesc.
  ///
  /// In en, this message translates to:
  /// **'Select boot image file to patch with Magisk'**
  String get patchBootImageDesc;

  /// No description provided for @fullUninstall.
  ///
  /// In en, this message translates to:
  /// **'Full Uninstall'**
  String get fullUninstall;

  /// No description provided for @fullUninstallDesc.
  ///
  /// In en, this message translates to:
  /// **'Remove Magisk and restore images'**
  String get fullUninstallDesc;

  /// No description provided for @removeOnly.
  ///
  /// In en, this message translates to:
  /// **'Remove Only'**
  String get removeOnly;

  /// No description provided for @removeOnlyDesc.
  ///
  /// In en, this message translates to:
  /// **'Remove Magisk without restoring images'**
  String get removeOnlyDesc;

  /// No description provided for @downloadInstall.
  ///
  /// In en, this message translates to:
  /// **'Download & Install'**
  String get downloadInstall;

  /// No description provided for @downloadInstallDesc.
  ///
  /// In en, this message translates to:
  /// **'Download latest version and install'**
  String get downloadInstallDesc;

  /// No description provided for @checkVersion.
  ///
  /// In en, this message translates to:
  /// **'Check Version'**
  String get checkVersion;

  /// No description provided for @checkVersionDesc.
  ///
  /// In en, this message translates to:
  /// **'Check latest available version'**
  String get checkVersionDesc;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @save.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// No description provided for @close.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// No description provided for @operationInProgress.
  ///
  /// In en, this message translates to:
  /// **'Operation in progress...'**
  String get operationInProgress;

  /// No description provided for @operationCompleted.
  ///
  /// In en, this message translates to:
  /// **'Operation completed successfully!'**
  String get operationCompleted;

  /// No description provided for @operationFailed.
  ///
  /// In en, this message translates to:
  /// **'Operation failed!'**
  String get operationFailed;

  /// No description provided for @starting.
  ///
  /// In en, this message translates to:
  /// **'Starting'**
  String get starting;

  /// No description provided for @error.
  ///
  /// In en, this message translates to:
  /// **'Error'**
  String get error;

  /// No description provided for @otaSlotSwitch.
  ///
  /// In en, this message translates to:
  /// **'OTA Slot Switch'**
  String get otaSlotSwitch;

  /// No description provided for @otaSlotSwitchDesc.
  ///
  /// In en, this message translates to:
  /// **'Switch to inactive slot and install Magisk'**
  String get otaSlotSwitchDesc;

  /// No description provided for @installAddonD.
  ///
  /// In en, this message translates to:
  /// **'Install addon.d Script'**
  String get installAddonD;

  /// No description provided for @installAddonDDesc.
  ///
  /// In en, this message translates to:
  /// **'Install OTA recovery script for Magisk'**
  String get installAddonDDesc;

  /// No description provided for @restoreMagiskAfterOta.
  ///
  /// In en, this message translates to:
  /// **'Restore Magisk After OTA'**
  String get restoreMagiskAfterOta;

  /// No description provided for @restoreMagiskAfterOtaDesc.
  ///
  /// In en, this message translates to:
  /// **'Restore Magisk after system OTA update'**
  String get restoreMagiskAfterOtaDesc;

  /// No description provided for @zygiskDesc.
  ///
  /// In en, this message translates to:
  /// **'Enable or disable Zygisk'**
  String get zygiskDesc;

  /// No description provided for @denyListDesc.
  ///
  /// In en, this message translates to:
  /// **'Enable or disable DenyList'**
  String get denyListDesc;

  /// No description provided for @aggressiveHide.
  ///
  /// In en, this message translates to:
  /// **'Aggressive Hide'**
  String get aggressiveHide;

  /// No description provided for @aggressiveHideDesc.
  ///
  /// In en, this message translates to:
  /// **'Enhanced hiding: spoof props, clear env vars'**
  String get aggressiveHideDesc;

  /// No description provided for @enableDenyOrSuList.
  ///
  /// In en, this message translates to:
  /// **'Enable DenyList or SuList above to manage apps.'**
  String get enableDenyOrSuList;

  /// No description provided for @installModule.
  ///
  /// In en, this message translates to:
  /// **'Install Module'**
  String get installModule;

  /// No description provided for @installModuleDesc.
  ///
  /// In en, this message translates to:
  /// **'Install a Magisk module from zip file'**
  String get installModuleDesc;

  /// No description provided for @selectModuleZip.
  ///
  /// In en, this message translates to:
  /// **'Select Module Zip'**
  String get selectModuleZip;

  /// No description provided for @selectModuleZipDesc.
  ///
  /// In en, this message translates to:
  /// **'Choose a .zip file to install as Magisk module'**
  String get selectModuleZipDesc;

  /// No description provided for @rootRequired.
  ///
  /// In en, this message translates to:
  /// **'Root access is required for this operation'**
  String get rootRequired;

  /// No description provided for @magiskRootRequired.
  ///
  /// In en, this message translates to:
  /// **'Magisk Root permission is required for this feature'**
  String get magiskRootRequired;

  /// No description provided for @noFileSelected.
  ///
  /// In en, this message translates to:
  /// **'No file selected'**
  String get noFileSelected;

  /// No description provided for @enforceDenyList.
  ///
  /// In en, this message translates to:
  /// **'Enforce DenyList'**
  String get enforceDenyList;

  /// No description provided for @enforceDenyListDesc.
  ///
  /// In en, this message translates to:
  /// **'Apps in DenyList will have root hidden from them'**
  String get enforceDenyListDesc;

  /// No description provided for @activities.
  ///
  /// In en, this message translates to:
  /// **'Activities'**
  String get activities;
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>['en', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {


  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en': return AppLocalizationsEn();
    case 'zh': return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.'
  );
}
