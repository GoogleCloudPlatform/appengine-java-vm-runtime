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

# source the supported feature JVM arguments
source /setup-env.bash
  
# use generated fast cli:
cd /var/lib/jetty
# TODO(ludo) remove when we upgrade to a newer Jetty version, see issue #171
sed -i -e 's/jar:${WAR}/jar:file:${WAR}/' /app/WEB-INF/quickstart-web.xml
source /var/lib/jetty/jetty_cmd.sh
