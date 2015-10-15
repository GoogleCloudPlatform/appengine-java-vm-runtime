#!/bin/bash

#
# Formats Java command line option to enable ALPN
#
# The script assumes that AppEngine specific environment variables are set.
#
# Usage example:
#     java $( /usr/local/alpn/format-env-appengine-vm.sh ) -cp ...
#

if [[ -n "${ALPN_DISABLE}" ]]; then
  >&2 echo "ALPN_DISABLE is set, ALPN will not be loaded"
  exit
fi

ALPN_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ARGS="-Xbootclasspath/p:${ALPN_ROOT}/alpn-boot.jar"

echo "${ARGS}"

