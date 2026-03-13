##################################
# Magisk app internal scripts
##################################

# $1 = delay
# $2 = command
run_delay() {
  (sleep $1; $2)&
}

# $1 = version string
# $2 = version code
env_check() {
  for file in busybox magiskboot magiskinit util_functions.sh boot_patch.sh; do
    [ -f "$MAGISKBIN/$file" ] || return 1
  done
  if [ "$2" -ge 25000 ]; then
    [ -f "$MAGISKBIN/magiskpolicy" ] || return 1
  fi
  if [ "$2" -ge 25210 ]; then
    [ -b "$MAGISKTMP/.magisk/device/preinit" ] || [ -b "$MAGISKTMP/.magisk/block/preinit" ] || return 2
  fi
  grep -xqF "MAGISK_VER='$1'" "$MAGISKBIN/util_functions.sh" || return 3
  grep -xqF "MAGISK_VER_CODE=$2" "$MAGISKBIN/util_functions.sh" || return 3
  return 0
}

# $1 = dir to copy
# $2 = destination (optional)
cp_readlink() {
  if [ -z $2 ]; then
    cd $1
  else
    cp -af $1/. $2
    cd $2
  fi
  for file in *; do
    if [ -L $file ]; then
      local full=$(readlink -f $file)
      rm $file
      cp -af $full $file
    fi
  done
  chmod -R 755 .
  cd /
}

# $1 = install dir
fix_env() {
  # Cleanup and make dirs
  rm -rf $MAGISKBIN/*
  mkdir -p $MAGISKBIN 2>/dev/null
  chmod 700 /data/adb
  cp_readlink $1 $MAGISKBIN
  rm -rf $1
  chown -R 0:0 $MAGISKBIN
}

# $1 = install dir
# $2 = boot partition
direct_install() {
  echo "- Flashing new boot image"
  flash_image $1/new-boot.img $2
  case $? in
    1)
      echo "! Insufficient partition size"
      return 1
      ;;
    2)
      echo "! $2 is read only"
      return 2
      ;;
  esac

  rm -f $1/new-boot.img
  fix_env $1
  run_migrations

  return 0
}

# $1 = uninstaller zip
run_uninstaller() {
  rm -rf /dev/tmp
  mkdir -p /dev/tmp/install
  unzip -o "$1" "assets/*" "lib/*" -d /dev/tmp/install
  INSTALLER=/dev/tmp/install sh /dev/tmp/install/assets/uninstaller.sh dummy 1 "$1"
}

# $1 = boot partition
restore_imgs() {
  local SHA1=$(grep_prop SHA1 $MAGISKTMP/.magisk/config)
  local BACKUPDIR=/data/magisk_backup_$SHA1
  [ -d $BACKUPDIR ] || return 1
  [ -f $BACKUPDIR/boot.img.gz ] || return 1
  flash_image $BACKUPDIR/boot.img.gz $1
}

# $1 = path to bootctl executable
post_ota() {
  cd /data/adb
  cp -f $1 bootctl
  rm -f $1
  chmod 755 bootctl
  if ! ./bootctl hal-info; then
    rm -f bootctl
    return
  fi
  SLOT_NUM=0
  [ $(./bootctl get-current-slot) -eq 0 ] && SLOT_NUM=1
  ./bootctl set-active-boot-slot $SLOT_NUM
  cat << EOF > post-fs-data.d/post_ota.sh
/data/adb/bootctl mark-boot-successful
rm -f /data/adb/bootctl
rm -f /data/adb/post-fs-data.d/post_ota.sh
EOF
  chmod 755 post-fs-data.d/post_ota.sh
  cd /
}

# $1 = APK
# $2 = package name
adb_pm_install() {
  local tmp=/data/local/tmp/temp.apk
  cp -f "$1" $tmp
  chmod 644 $tmp
  su 2000 -c pm install -g $tmp || pm install -g $tmp || su 1000 -c pm install -g $tmp
  local res=$?
  rm -f $tmp
  if [ $res = 0 ]; then
    appops set "$2" REQUEST_INSTALL_PACKAGES allow
  fi
  return $res
}

check_boot_ramdisk() {
  # Create boolean ISAB
  ISAB=true
  [ -z $SLOT ] && ISAB=false

  # If we are A/B, then we must have ramdisk
  $ISAB && return 0

  # If we are using legacy SAR, but not A/B, assume we do not have ramdisk
  if $LEGACYSAR; then
    # Override recovery mode to true
    RECOVERYMODE=true
    return 1
  fi

  return 0
}

check_encryption() {
  if $ISENCRYPTED; then
    if [ $SDK_INT -lt 24 ]; then
      CRYPTOTYPE="block"
    else
      # First see what the system tells us
      CRYPTOTYPE=$(getprop ro.crypto.type)
      if [ -z $CRYPTOTYPE ]; then
        # If not mounting through device mapper, we are FBE
        if grep ' /data ' /proc/mounts | grep -qv 'dm-'; then
          CRYPTOTYPE="file"
        else
          # We are either FDE or metadata encryption (which is also FBE)
          CRYPTOTYPE="block"
          grep -q ' /metadata ' /proc/mounts && CRYPTOTYPE="file"
        fi
      fi
    fi
  else
    CRYPTOTYPE="N/A"
  fi
}

printvar() {
  eval echo $1=\$$1
}

run_action() {
  local MODID="$1"
  cd "/data/adb/modules/$MODID"
  sh ./action.sh
  local RES=$?
  cd /
  return $RES
}

##########################
# Non-root util_functions
##########################

mount_partitions() {
  [ "$(getprop ro.build.ab_update)" = "true" ] && SLOT=$(getprop ro.boot.slot_suffix)
  # Check whether non rootfs root dir exists
  SYSTEM_AS_ROOT=false
  grep ' / ' /proc/mounts | grep -qv 'rootfs' && SYSTEM_AS_ROOT=true

  LEGACYSAR=false
  grep ' / ' /proc/mounts | grep -q '/dev/root' && LEGACYSAR=true
}

get_flags() {
  KEEPVERITY=$SYSTEM_AS_ROOT
  ISENCRYPTED=false
  [ "$(getprop ro.crypto.state)" = "encrypted" ] && ISENCRYPTED=true
  KEEPFORCEENCRYPT=$ISENCRYPTED
  if [ -n "$(getprop ro.boot.vbmeta.device)" -o -n "$(getprop ro.boot.vbmeta.size)" ]; then
    PATCHVBMETAFLAG=false
  elif getprop ro.product.ab_ota_partitions | grep -wq vbmeta; then
    PATCHVBMETAFLAG=false
  else
    PATCHVBMETAFLAG=true
  fi
  [ -z $RECOVERYMODE ] && RECOVERYMODE=false
  [ -z $VENDORBOOT ] && VENDORBOOT=false
}

run_migrations() { return; }

grep_prop() { return; }

#############
# Initialize
#############

app_init() {
  mount_partitions >/dev/null
  RAMDISKEXIST=false
  check_boot_ramdisk && RAMDISKEXIST=true
  get_flags >/dev/null
  run_migrations >/dev/null
  check_encryption

  # Dump variables
  printvar SLOT
  printvar SYSTEM_AS_ROOT
  printvar RAMDISKEXIST
  printvar ISAB
  printvar CRYPTOTYPE
  printvar PATCHVBMETAFLAG
  printvar LEGACYSAR
  printvar RECOVERYMODE
  printvar KEEPVERITY
  printvar KEEPFORCEENCRYPT
  printvar VENDORBOOT
}

export BOOTMODE=true

##########################
# Root Access Management
##########################

# Get list of apps with root access granted
# Returns: package_name per line
get_root_access_apps() {
  local db_file="/data/adb/magisk.db"
  
  if [ -f "$db_file" ]; then
    # Method 1: Use magisk --sqlite command
    local result
    result=$(magisk --sqlite "SELECT package FROM policies WHERE policy > 0" 2>/dev/null)
    if [ -n "$result" ]; then
      echo "$result"
      return 0
    fi
    
    # Method 2: Use sqlite3 directly
    if command -v sqlite3 >/dev/null 2>&1; then
      sqlite3 "$db_file" "SELECT package FROM policies WHERE policy > 0" 2>/dev/null
      return 0
    fi
  fi
  
  return 1
}

# Grant root access to an app
# $1 = package name
# Returns: 0 on success, 1 on failure
grant_root_access() {
  local pkg="$1"
  [ -z "$pkg" ] && return 1
  
  local db_file="/data/adb/magisk.db"
  
  # Method 1: Use magisk --su command
  if magisk --su add "$pkg" 2>/dev/null; then
    return 0
  fi
  
  # Method 2: Use magisk --sqlite command
  if magisk --sqlite "INSERT OR REPLACE INTO policies (package_name, policy, until) VALUES ('$pkg', 2, 0)" 2>/dev/null; then
    return 0
  fi
  
  # Method 3: Direct database manipulation
  if [ -f "$db_file" ] && command -v sqlite3 >/dev/null 2>&1; then
    sqlite3 "$db_file" "INSERT OR REPLACE INTO policies (package_name, policy, until) VALUES ('$pkg', 2, 0)" 2>/dev/null
    return $?
  fi
  
  return 1
}

# Revoke root access from an app
# $1 = package name
# Returns: 0 on success, 1 on failure
revoke_root_access() {
  local pkg="$1"
  [ -z "$pkg" ] && return 1
  
  local db_file="/data/adb/magisk.db"
  
  # Method 1: Use magisk --su command
  if magisk --su remove "$pkg" 2>/dev/null; then
    return 0
  fi
  
  # Method 2: Use magisk --sqlite command
  if magisk --sqlite "DELETE FROM policies WHERE package_name = '$pkg'" 2>/dev/null; then
    return 0
  fi
  
  # Method 3: Direct database manipulation
  if [ -f "$db_file" ] && command -v sqlite3 >/dev/null 2>&1; then
    sqlite3 "$db_file" "DELETE FROM policies WHERE package_name = '$pkg'" 2>/dev/null
    return $?
  fi
  
  return 1
}

# Check if an app has root access
# $1 = package name
# Returns: 0 if has root access, 1 if not
has_root_access() {
  local pkg="$1"
  [ -z "$pkg" ] && return 1
  
  local db_file="/data/adb/magisk.db"
  
  if [ -f "$db_file" ]; then
    # Method 1: Use magisk --sqlite command
    local policy
    policy=$(magisk --sqlite "SELECT policy FROM policies WHERE package_name = '$pkg'" 2>/dev/null)
    if [ -n "$policy" ] && [ "$policy" -gt 0 ] 2>/dev/null; then
      return 0
    fi
    
    # Method 2: Use sqlite3 directly
    if command -v sqlite3 >/dev/null 2>&1; then
      policy=$(sqlite3 "$db_file" "SELECT policy FROM policies WHERE package_name = '$pkg'" 2>/dev/null)
      if [ -n "$policy" ] && [ "$policy" -gt 0 ] 2>/dev/null; then
        return 0
      fi
    fi
  fi
  
  return 1
}

# Get root access policy for an app
# $1 = package name
# Returns: policy value (0=deny, 1=allow, 2=allow_forever, 3=allow_session)
get_root_policy() {
  local pkg="$1"
  [ -z "$pkg" ] && echo "0" && return
  
  local db_file="/data/adb/magisk.db"
  
  if [ -f "$db_file" ]; then
    # Method 1: Use magisk --sqlite command
    local policy
    policy=$(magisk --sqlite "SELECT policy FROM policies WHERE package_name = '$pkg'" 2>/dev/null)
    if [ -n "$policy" ]; then
      echo "$policy"
      return
    fi
    
    # Method 2: Use sqlite3 directly
    if command -v sqlite3 >/dev/null 2>&1; then
      policy=$(sqlite3 "$db_file" "SELECT policy FROM policies WHERE package_name = '$pkg'" 2>/dev/null)
      if [ -n "$policy" ]; then
        echo "$policy"
        return
      fi
    fi
  fi
  
  echo "0"
}

# Get all root access policies (for logging/debugging)
# Returns: package_name:policy per line
list_root_policies() {
  local db_file="/data/adb/magisk.db"
  
  if [ -f "$db_file" ]; then
    # Method 1: Use magisk --sqlite command
    local result
    result=$(magisk --sqlite "SELECT package_name || ':' || policy FROM policies" 2>/dev/null)
    if [ -n "$result" ]; then
      echo "$result"
      return 0
    fi
    
    # Method 2: Use sqlite3 directly
    if command -v sqlite3 >/dev/null 2>&1; then
      sqlite3 "$db_file" "SELECT package_name || ':' || policy FROM policies" 2>/dev/null
      return 0
    fi
  fi
  
  return 1
}

# Notify Magisk daemon of policy changes
notify_policy_change() {
  # Restart magiskd to reload policies
  killall magiskd 2>/dev/null || true
  return 0
}

##########################
# Zygisk Configuration Management
##########################

# Check if Zygisk is enabled
# Returns: 0 if enabled, 1 if disabled
is_zygisk_enabled() {
  local db_file="/data/adb/magisk.db"
  
  # Method 1: Check settings table in magisk.db
  if [ -f "$db_file" ]; then
    local zygisk_value
    zygisk_value=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'zygisk' LIMIT 1" 2>/dev/null)
    if [ -n "$zygisk_value" ] && [ "$zygisk_value" = "1" ]; then
      return 0
    fi
    
    # Try alternative key names
    zygisk_value=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'zygisk_enabled' LIMIT 1" 2>/dev/null)
    if [ -n "$zygisk_value" ] && [ "$zygisk_value" = "1" ]; then
      return 0
    fi
  fi
  
  # Method 2: Check /data/adb/zygisk file (older versions)
  if [ -f "/data/adb/zygisk" ]; then
    local zygisk_content
    zygisk_content=$(cat /data/adb/zygisk 2>/dev/null)
    if [ "$zygisk_content" = "1" ]; then
      return 0
    fi
  fi
  
  # Method 3: Check if Zygisk modules directory exists and has content
  if [ -d "/data/adb/zygisk/modules" ]; then
    if [ -n "$(ls -A /data/adb/zygisk/modules 2>/dev/null)" ]; then
      return 0
    fi
  fi
  
  return 1
}

# Enable or disable Zygisk
# $1 = 1 to enable, 0 to disable
# Returns: 0 on success, 1 on failure
set_zygisk_enabled() {
  local enable="$1"
  [ "$enable" != "1" ] && [ "$enable" != "0" ] && return 1
  
  local db_file="/data/adb/magisk.db"
  local success=0
  
  # Method 1: Update settings table in magisk.db
  if [ -f "$db_file" ]; then
    # Try both key names for compatibility
    if magisk --sqlite "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk', '$enable')" 2>/dev/null; then
      success=1
    elif magisk --sqlite "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '$enable')" 2>/dev/null; then
      success=1
    fi
    
    if [ "$success" = "1" ]; then
      # Verify the setting was applied
      local verify_value
      verify_value=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'zygisk' LIMIT 1" 2>/dev/null)
      if [ -z "$verify_value" ]; then
        verify_value=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'zygisk_enabled' LIMIT 1" 2>/dev/null)
      fi
      
      if [ "$verify_value" = "$enable" ]; then
        # Restart Magisk daemon to apply changes
        killall magiskd 2>/dev/null || true
        sleep 1
        # Start new daemon
        magisk --daemon 2>/dev/null || true
        return 0
      fi
    fi
  fi
  
  # Method 2: Update /data/adb/zygisk file (fallback for older versions)
  if echo "$enable" > /data/adb/zygisk 2>/dev/null; then
    chmod 644 /data/adb/zygisk 2>/dev/null
    # Restart Magisk daemon
    killall magiskd 2>/dev/null || true
    sleep 1
    magisk --daemon 2>/dev/null || true
    return 0
  fi
  
  return 1
}

##########################
# DenyList Configuration Management
##########################

# Check if DenyList is enabled
# Returns: 0 if enabled, 1 if disabled
is_denylist_enabled() {
  local db_file="/data/adb/magisk.db"
  
  # Method 1: Check settings table in magisk.db
  if [ -f "$db_file" ]; then
    local denylist_value
    denylist_value=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'denylist' LIMIT 1" 2>/dev/null)
    if [ -n "$denylist_value" ] && [ "$denylist_value" = "1" ]; then
      return 0
    fi
  fi
  
  # Method 2: Check if denylist table has entries
  if [ -f "$db_file" ]; then
    local count
    count=$(magisk --sqlite "SELECT COUNT(*) FROM denylist" 2>/dev/null)
    if [ -n "$count" ] && [ "$count" -gt 0 ] 2>/dev/null; then
      return 0
    fi
  fi
  
  # Method 3: Check /data/adb/denylist file (older versions)
  if [ -f "/data/adb/denylist" ]; then
    return 0
  fi
  
  return 1
}

# Enable or disable DenyList
# $1 = 1 to enable, 0 to disable
# Returns: 0 on success, 1 on failure
set_denylist_enabled() {
  local enable="$1"
  [ "$enable" != "1" ] && [ "$enable" != "0" ] && return 1
  
  local db_file="/data/adb/magisk.db"
  local success=0
  
  # Method 1: Update settings table in magisk.db
  if [ -f "$db_file" ]; then
    if magisk --sqlite "INSERT OR REPLACE INTO settings (key, value) VALUES ('denylist', '$enable')" 2>/dev/null; then
      success=1
    fi
    
    if [ "$success" = "1" ]; then
      # Verify the setting was applied
      local verify_value
      verify_value=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'denylist' LIMIT 1" 2>/dev/null)
      
      if [ "$verify_value" = "$enable" ]; then
        # Restart Magisk daemon to apply changes
        killall magiskd 2>/dev/null || true
        sleep 1
        magisk --daemon 2>/dev/null || true
        return 0
      fi
    fi
  fi
  
  # Method 2: Create/remove /data/adb/denylist file (fallback for older versions)
  if [ "$enable" = "1" ]; then
    if touch /data/adb/denylist 2>/dev/null; then
      chmod 644 /data/adb/denylist 2>/dev/null
      # Restart Magisk daemon
      killall magiskd 2>/dev/null || true
      sleep 1
      magisk --daemon 2>/dev/null || true
      return 0
    fi
  else
    if rm -f /data/adb/denylist 2>/dev/null; then
      # Restart Magisk daemon
      killall magiskd 2>/dev/null || true
      sleep 1
      magisk --daemon 2>/dev/null || true
      return 0
    fi
  fi
  
  return 1
}
