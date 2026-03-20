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
# Note: Magisk policies table uses UID, not package name
##########################

# Get UID from package name
# $1 = package name
# Returns: UID or empty string if not found
get_uid_from_package() {
  local pkg="$1"
  [ -z "$pkg" ] && return 1
  
  # Use dumpsys package to get userId (which is the UID for user 0)
  local uid
  uid=$(dumpsys package "$pkg" 2>/dev/null | grep "userId=" | head -1 | sed 's/.*userId=//')
  
  if [ -n "$uid" ]; then
    echo "$uid"
    return 0
  fi
  
  # Fallback: use pm list packages -U
  uid=$(pm list packages -U 2>/dev/null | grep "package:$pkg " | sed 's/.*uid://')
  if [ -n "$uid" ]; then
    echo "$uid"
    return 0
  fi
  
  return 1
}

# Get package name from UID
# $1 = UID
# Returns: package name or empty string if not found
get_package_from_uid() {
  local uid="$1"
  [ -z "$uid" ] && return 1
  
  # Use pm list packages -U to find package by UID
  local pkg
  pkg=$(pm list packages -U 2>/dev/null | grep "uid:$uid " | sed 's/package://' | sed 's/ uid:.*//')
  
  if [ -n "$pkg" ]; then
    echo "$pkg"
    return 0
  fi
  
  # Fallback: use dumpsys package for all packages
  for p in $(pm list packages 2>/dev/null | sed 's/package://'); do
    local puid
    puid=$(dumpsys package "$p" 2>/dev/null | grep "userId=" | head -1 | sed 's/.*userId=//')
    if [ "$puid" = "$uid" ]; then
      echo "$p"
      return 0
    fi
  done
  
  return 1
}

# Get list of apps with root access granted
# Returns: package_name per line
get_root_access_apps() {
  local db_file="/data/adb/magisk.db"
  local found=0
  
  if [ -f "$db_file" ]; then
    # Get UIDs with root access from policies table
    local output
    
    # Method 1: Use magisk --sqlite (output format: uid=12345)
    output=$(magisk --sqlite 'SELECT uid FROM policies WHERE policy > 0' 2>/dev/null)
    
    if [ -n "$output" ]; then
      for line in $output; do
        # Parse format: uid=12345
        local uid
        case "$line" in
          uid=*)
            uid="${line#uid=}"
            ;;
          *=*)
            # Handle other key=value formats
            continue
            ;;
          *)
            # Plain number
            uid="$line"
            ;;
        esac
        
        # Skip empty or invalid UIDs
        [ -z "$uid" ] && continue
        
        # Check if it's a valid number and >= 10000
        case "$uid" in
          ''|*[!0-9]*) continue ;;
        esac
        [ "$uid" -lt 10000 ] && continue  # Skip system UIDs
        
        local pkg
        pkg=$(get_package_from_uid "$uid")
        if [ -n "$pkg" ]; then
          echo "$pkg"
          found=1
        fi
      done
    fi
    
    # Method 2: Fallback to sqlite3 directly
    if [ "$found" = "0" ] && command -v sqlite3 >/dev/null 2>&1; then
      output=$(sqlite3 "$db_file" 'SELECT uid FROM policies WHERE policy > 0' 2>/dev/null)
      
      if [ -n "$output" ]; then
        for uid in $output; do
          [ -z "$uid" ] && continue
          case "$uid" in
            ''|*[!0-9]*) continue ;;
          esac
          [ "$uid" -lt 10000 ] && continue
          
          local pkg
          pkg=$(get_package_from_uid "$uid")
          if [ -n "$pkg" ]; then
            echo "$pkg"
            found=1
          fi
        done
      fi
    fi
    
    [ "$found" = "1" ] && return 0
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
  
  # Get UID from package name
  local uid
  uid=$(get_uid_from_package "$pkg")
  [ -z "$uid" ] && return 1
  
  # Method 1: Use magisk --sqlite command with UID
  if magisk --sqlite "INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)" 2>/dev/null; then
    return 0
  fi
  
  # Method 2: Direct database manipulation with UID
  if [ -f "$db_file" ] && command -v sqlite3 >/dev/null 2>&1; then
    sqlite3 "$db_file" "INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)" 2>/dev/null
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
  
  # Get UID from package name
  local uid
  uid=$(get_uid_from_package "$pkg")
  [ -z "$uid" ] && return 1
  
  local success=0
  
  # Method 1: Use magisk --sqlite command with UID
  # Note: magisk --sqlite returns 0 even if no rows affected, so we need to verify
  magisk --sqlite "DELETE FROM policies WHERE uid = $uid" 2>/dev/null
  
  # Verify deletion using magisk --sqlite
  local verify
  verify=$(magisk --sqlite "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
  # Handle output format: could be "policy=0", "0", or empty
  case "$verify" in
    ""|policy=0|0)
      success=1
      ;;
  esac
  
  # Method 2: Direct database manipulation with UID if Method 1 failed
  if [ "$success" = "0" ] && [ -f "$db_file" ] && command -v sqlite3 >/dev/null 2>&1; then
    sqlite3 "$db_file" "DELETE FROM policies WHERE uid = $uid" 2>/dev/null
    
    # Verify deletion
    verify=$(sqlite3 "$db_file" "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
    if [ -z "$verify" ] || [ "$verify" = "0" ]; then
      success=1
    fi
  fi
  
  # Method 3: Try setting policy to 0 (deny) instead of deleting
  if [ "$success" = "0" ]; then
    magisk --sqlite "UPDATE policies SET policy = 0 WHERE uid = $uid" 2>/dev/null
    
    # Verify update
    verify=$(magisk --sqlite "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
    # Handle output format: could be "policy=0", "0", etc.
    case "$verify" in
      policy=0|0|"")
        success=1
        ;;
    esac
  fi
  
  # Method 4: Direct update using sqlite3
  if [ "$success" = "0" ] && [ -f "$db_file" ] && command -v sqlite3 >/dev/null 2>&1; then
    sqlite3 "$db_file" "UPDATE policies SET policy = 0 WHERE uid = $uid" 2>/dev/null
    
    # Verify update
    verify=$(sqlite3 "$db_file" "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
    if [ "$verify" = "0" ] || [ -z "$verify" ]; then
      success=1
    fi
  fi
  
  if [ "$success" = "1" ]; then
    # Notify Magisk daemon to reload policies
    # Method 1: Use magisk --sqlite to trigger reload
    magisk --sqlite "SELECT 1" 2>/dev/null
    
    # Method 2: Restart magiskd (more reliable)
    killall -HUP magiskd 2>/dev/null || kill -HUP $(pgrep magiskd | head -1) 2>/dev/null || true
    
    return 0
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
  
  # Get UID from package name
  local uid
  uid=$(get_uid_from_package "$pkg")
  [ -z "$uid" ] && return 1
  
  if [ -f "$db_file" ]; then
    # Method 1: Use magisk --sqlite command with UID
    local policy
    policy=$(magisk --sqlite "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
    if [ -n "$policy" ] && [ "$policy" -gt 0 ] 2>/dev/null; then
      return 0
    fi
    
    # Method 2: Use sqlite3 directly with UID
    if command -v sqlite3 >/dev/null 2>&1; then
      policy=$(sqlite3 "$db_file" "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
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
  
  # Get UID from package name
  local uid
  uid=$(get_uid_from_package "$pkg")
  [ -z "$uid" ] && echo "0" && return
  
  if [ -f "$db_file" ]; then
    # Method 1: Use magisk --sqlite command with UID
    local policy
    policy=$(magisk --sqlite "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
    if [ -n "$policy" ]; then
      echo "$policy"
      return
    fi
    
    # Method 2: Use sqlite3 directly with UID
    if command -v sqlite3 >/dev/null 2>&1; then
      policy=$(sqlite3 "$db_file" "SELECT policy FROM policies WHERE uid = $uid" 2>/dev/null)
      if [ -n "$policy" ]; then
        echo "$policy"
        return
      fi
    fi
  fi
  
  echo "0"
}

# Get all root access policies (for logging/debugging)
# Returns: package_name:uid:policy per line
list_root_policies() {
  local db_file="/data/adb/magisk.db"
  
  if [ -f "$db_file" ]; then
    # Get all policies with UID
    local policies
    policies=$(magisk --sqlite "SELECT uid || ':' || policy FROM policies" 2>/dev/null)
    
    if [ -z "$policies" ]; then
      policies=$(sqlite3 "$db_file" "SELECT uid || ':' || policy FROM policies" 2>/dev/null)
    fi
    
    # Convert UIDs to package names
    if [ -n "$policies" ]; then
      for entry in $policies; do
        local uid=$(echo "$entry" | cut -d':' -f1)
        local policy=$(echo "$entry" | cut -d':' -f2)
        
        [ -z "$uid" ] && continue
        
        local pkg
        pkg=$(get_package_from_uid "$uid")
        if [ -n "$pkg" ]; then
          echo "$pkg:$uid:$policy"
        else
          echo "unknown:$uid:$policy"
        fi
      done
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
  
  # Method 1: Use magisk --sqlite command
  # Note: magisk --sqlite always returns 0 if command executes, so we need to verify
  magisk --sqlite "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk', '$enable')" 2>/dev/null
  magisk --sqlite "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '$enable')" 2>/dev/null
  
  # Verify the setting was applied
  local verify
  verify=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'zygisk'" 2>/dev/null)
  
  # Handle output format: could be "value=1", "1", or empty
  case "$verify" in
    value=$enable|$enable)
      success=1
      ;;
  esac
  
  # Method 2: Direct sqlite3 manipulation if Method 1 verification failed
  if [ "$success" = "0" ] && [ -f "$db_file" ] && command -v sqlite3 >/dev/null 2>&1; then
    sqlite3 "$db_file" "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk', '$enable')" 2>/dev/null
    sqlite3 "$db_file" "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '$enable')" 2>/dev/null
    
    # Verify again
    verify=$(sqlite3 "$db_file" "SELECT value FROM settings WHERE key = 'zygisk'" 2>/dev/null)
    if [ "$verify" = "$enable" ]; then
      success=1
    fi
  fi
  
  if [ "$success" = "1" ]; then
    # Notify Magisk daemon to reload settings (don't kill, just send HUP signal)
    kill -HUP $(pgrep -x magiskd | head -1) 2>/dev/null || true
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
  
  # Method 1: Use magisk --denylist command (official way)
  if [ "$enable" = "1" ]; then
    magisk --denylist enable 2>/dev/null
  else
    magisk --denylist disable 2>/dev/null
  fi
  
  # Verify using magisk --denylist status
  local status
  status=$(magisk --denylist status 2>/dev/null)
  case "$status" in
    *enabled*|*true*|*1*)
      if [ "$enable" = "1" ]; then
        success=1
      fi
      ;;
    *disabled*|*false*|*0*)
      if [ "$enable" = "0" ]; then
        success=1
      fi
      ;;
  esac
  
  # Method 2: Use magisk --sqlite to update settings and verify
  if [ "$success" = "0" ]; then
    magisk --sqlite "INSERT OR REPLACE INTO settings (key, value) VALUES ('denylist', '$enable')" 2>/dev/null
    magisk --sqlite "INSERT OR REPLACE INTO settings (key, value) VALUES ('magiskhide', '$enable')" 2>/dev/null
    
    # Verify the setting
    local verify
    verify=$(magisk --sqlite "SELECT value FROM settings WHERE key = 'denylist'" 2>/dev/null)
    
    case "$verify" in
      value=$enable|$enable)
        success=1
        ;;
    esac
  fi
  
  # Method 3: Direct sqlite3 manipulation
  if [ "$success" = "0" ] && [ -f "$db_file" ] && command -v sqlite3 >/dev/null 2>&1; then
    sqlite3 "$db_file" "INSERT OR REPLACE INTO settings (key, value) VALUES ('denylist', '$enable')" 2>/dev/null
    sqlite3 "$db_file" "INSERT OR REPLACE INTO settings (key, value) VALUES ('magiskhide', '$enable')" 2>/dev/null
    
    # Verify
    verify=$(sqlite3 "$db_file" "SELECT value FROM settings WHERE key = 'denylist'" 2>/dev/null)
    if [ "$verify" = "$enable" ]; then
      success=1
    fi
  fi
  
  if [ "$success" = "1" ]; then
    # Notify Magisk daemon to reload settings (don't kill, just send HUP signal)
    kill -HUP $(pgrep -x magiskd | head -1) 2>/dev/null || true
    return 0
  fi
  
  return 1
}
