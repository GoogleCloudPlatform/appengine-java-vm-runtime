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

# source the supported feature JVM arguments
source /setup-env.bash
  
# use generated fast cli:
cd /var/lib/jetty
source /var/lib/jetty/jetty_cmd.sh
