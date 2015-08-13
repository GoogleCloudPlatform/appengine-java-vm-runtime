#!/bin/bash
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

# Increase initial permsize.
PERM_SIZE=64M  # Default = 21757952 (20.75M)
MAX_PERM_SIZE=166M  # Default = 174063616 (166M)

DBG_AGENT=

if [[ "$GAE_PARTITION" = "dev" ]]; then
  if [[ -n "$DBG_ENABLE" ]]; then
    echo "Running locally and DBG_ENABLE is set, enabling standard Java debugger agent"
    DBG_PORT=${DBG_PORT:-5005}
    DBG_AGENT="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DBG_PORT}"
  fi
else
  # Get OAuth token from metadata service.
  TOKEN_URL="http://metadata/computeMetadata/v1/instance/service-accounts/default/token"
  METADATA_HEADER="Metadata-Flavor: Google"
  OAUTH_TOKEN="$( wget -q -O - "$@" --no-cookies --header "${METADATA_HEADER}" "${TOKEN_URL}" | \
                  sed -e 's/.*"access_token"\ *:\ *"\([^"]*\)".*$/\1/g' )"

  # Download the agent
  CDBG_REF_URL="http://metadata/computeMetadata/v1/instance/attributes/gae_debugger_filename"
  if [[ -z "${CDBG_AGENT_URL}" ]]; then
    CDBG_AGENT_URL="https://storage.googleapis.com/vm-config.$(echo ${GAE_LONG_APP_ID} | sed -e 's/^\(.*\)\:\(.*\)$/\2.\1.a/g').appspot.com/"
    CDBG_AGENT_URL+="$( wget -q -O - "$@" --no-cookies --header "${METADATA_HEADER}" "${CDBG_REF_URL}" )"
  fi

  echo "Downloading Cloud Debugger agent from ${CDBG_AGENT_URL}"
  AUTH_HEADER="Authorization: Bearer ${OAUTH_TOKEN}"
  wget -O cdbg_java_agent.tar.gz -nv --no-cookies -t 3 --header "${AUTH_HEADER}" "${CDBG_AGENT_URL}"

  # Extract the agent and format the command line arguments.
  mkdir -p cdbg ; tar xzf cdbg_java_agent.tar.gz -C cdbg
  DBG_AGENT="$( cdbg/format-env-appengine-vm.sh )"
fi

PROF_AGENT=
# Download and install the cloud profiler agent if $CPROF_ENABLE is set
# CPROF_AGENT_URL can be set to download alternate versions of the agent
if [[ -n "${CPROF_ENABLE}" ]]; then
  if [[ -z "${CPROF_AGENT_URL}" ]] ; then
    CPROF_AGENT_URL="https://storage.googleapis.com/cloud-profiler/appengine-java/current/cloud_profiler_java_agent.tar.gz"
  fi

  echo "Downloading Cloud Profiler agent from ${CPROF_AGENT_URL}"
  wget -O cloud_profiler_java_agent.tar.gz -nv --no-cookies -t 3 "${CPROF_AGENT_URL}"

  # Extract the agent and format the command line arguments.
  mkdir -p cp ; tar xzf cloud_profiler_java_agent.tar.gz -C cp
  PROF_AGENT="$( cp/format-env-appengine-vm.sh )"
fi

# use generated fast cli:
cd /var/lib/jetty
source /var/lib/jetty/jetty_cmd.sh
