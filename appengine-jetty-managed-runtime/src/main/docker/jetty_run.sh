#!/bin/bash

# Test if the argument is executable and if then run it directly
if type "$1" &>/dev/null; then
  exec "$@"
fi

# Script that starts Jetty and sets Jetty specific system properties based on
# environment variables set for all runtime implementations.
if [ -z "$RUNTIME_DIR" ]; then
  echo "Error: Required environment variable RUNTIME_DIR is not set."
  exit 1
fi
HEAP_SIZE_FRAC=0.8
RAM_RESERVED_MB=400  # Ram used by containers outside of the app.
HEAP_SIZE=$(awk -v frac=$HEAP_SIZE_FRAC -v res=$RAM_RESERVED_MB /MemTotal/'{
  print int($2/1024*frac-res) "M" } ' /proc/meminfo)
echo "Info: Limiting Java heap size to: $HEAP_SIZE"

ALPN_BOOT=
if [[ -n "$ALPN_ENABLE" ]]; then
  ALPN_BOOT="$( /opt/alpn/format-env-appengine-vm.sh )"
fi

DBG_AGENT=
if [[ "$GAE_PARTITION" = "dev" ]]; then
  if [[ -n "$DBG_ENABLE" ]]; then
    echo "Running locally and DBG_ENABLE is set, enabling standard Java debugger agent"
    DBG_PORT=${DBG_PORT:-5005}
    DBG_AGENT="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DBG_PORT}"
  fi
else
  DBG_AGENT="$( /opt/cdbg/format-env-appengine-vm.sh )"
fi

PROF_AGENT=
if [[ -n "${CPROF_ENABLE}" ]]; then
  PROF_AGENT="$( /opt/cprof/format-env-appengine-vm.sh )"
fi

# use generated fast cli:
cd /var/lib/jetty
source /var/lib/jetty/jetty_cmd.sh
