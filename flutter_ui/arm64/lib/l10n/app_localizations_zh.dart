// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'MagisKube';

  @override
  String get dashboard => '仪表板';

  @override
  String get magiskManager => 'Magisk 管理器';

  @override
  String get modules => '模块';

  @override
  String get apps => '应用';

  @override
  String get denyList => '排除列表';

  @override
  String get logs => '日志';

  @override
  String get contributors => '贡献者';

  @override
  String get settings => '设置';

  @override
  String get theme => '主题';

  @override
  String get darkMode => '深色模式';

  @override
  String get enabled => '已启用';

  @override
  String get disabled => '已禁用';

  @override
  String get noModules => '未安装模块';

  @override
  String get noApps => '没有具有 root 权限的应用';

  @override
  String get installMagisk => '安装 Magisk';

  @override
  String get installMagiskDesc => '安装、升级或修补 Magisk';

  @override
  String get uninstallMagisk => '卸载 Magisk';

  @override
  String get uninstallMagiskDesc => '从设备中移除 Magisk';

  @override
  String get updateManager => '更新管理器';

  @override
  String get updateManagerDesc => '检查更新';

  @override
  String get magiskInfo => 'Magisk 信息';

  @override
  String get version => '版本';

  @override
  String get root => 'Root';

  @override
  String get zygisk => 'Zygisk';

  @override
  String get ramdisk => 'Ramdisk';

  @override
  String get yes => '是';

  @override
  String get no => '否';

  @override
  String get loaded => '已加载';

  @override
  String get notLoaded => '未加载';

  @override
  String get autoInstall => '自动安装';

  @override
  String get autoInstallDesc => '自动检测并安装 Magisk ';

  @override
  String get patchBootImage => '修补启动镜像';

  @override
  String get patchBootImageDesc => '选择启动镜像文件以使用 Magisk 进行修补';

  @override
  String get fullUninstall => '完全卸载';

  @override
  String get fullUninstallDesc => '移除 Magisk 并恢复镜像';

  @override
  String get removeOnly => '仅移除';

  @override
  String get removeOnlyDesc => '移除 Magisk 但不恢复镜像';

  @override
  String get downloadInstall => '下载并安装';

  @override
  String get downloadInstallDesc => '下载最新版本并安装';

  @override
  String get checkVersion => '检查版本';

  @override
  String get checkVersionDesc => '检查可用的最新版本';

  @override
  String get cancel => '取消';

  @override
  String get save => '保存';

  @override
  String get close => '关闭';

  @override
  String get operationInProgress => '操作进行中...';

  @override
  String get operationCompleted => '操作成功完成！';

  @override
  String get operationFailed => '操作失败！';

  @override
  String get starting => '开始';

  @override
  String get error => '错误';

  @override
  String get otaSlotSwitch => 'OTA槽位切换';

  @override
  String get otaSlotSwitchDesc => '切换到非活动槽位并安装 Magisk';

  @override
  String get installAddonD => '安装 addon.d 脚本';

  @override
  String get installAddonDDesc => '安装 Magisk OTA 恢复脚本';

  @override
  String get restoreMagiskAfterOta => 'OTA 后恢复 Magisk';

  @override
  String get restoreMagiskAfterOtaDesc => '在系统 OTA 更新后恢复 Magisk';

  @override
  String get zygiskDesc => '启用或禁用 Zygisk';

  @override
  String get denyListDesc => '启用或禁用 DenyList';

  @override
  String get installModule => '安装模块';

  @override
  String get installModuleDesc => '从 zip 文件安装 Magisk 模块';

  @override
  String get selectModuleZip => '选择模块 Zip';

  @override
  String get selectModuleZipDesc => '选择一个 .zip 文件作为 Magisk 模块安装';

  @override
  String get rootRequired => '此操作需要 Root 权限';
}
