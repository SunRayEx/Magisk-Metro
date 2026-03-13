#!/sbin/sh

#################
# Initialization
#################

umask 022

# echo before loading util_functions
ui_print() { echo "$1"; }

require_new_magisk() {
  ui_print "*******************************"
  ui_print " Please install Magisk v20.4+! "
  ui_print "*******************************"
  exit 1
}

#########################
# Load util_functions.sh
#########################

OUTFD=$2
ZIPFILE=$3

mount /data 2>/dev/null

# Check if we're running from temp directory (for module installation from app)
if [ -f "./util_functions.sh" ]; then
  # Use local util_functions.sh from temp directory
  . ./util_functions.sh
elif [ -f "/data/adb/magisk/util_functions.sh" ]; then
  # Use system util_functions.sh (standard Magisk installation)
  . /data/adb/magisk/util_functions.sh
else
  require_new_magisk
fi

[ $MAGISK_VER_CODE -lt 20400 ] && require_new_magisk

install_module
exit 0
