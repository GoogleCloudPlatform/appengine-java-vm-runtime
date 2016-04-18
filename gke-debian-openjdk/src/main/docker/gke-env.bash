#!/bin/bash
ALPN_BOOT=
if [[ -n "$ALPN_ENABLE" ]]; then
  ALPN_BOOT="$( /opt/alpn/format-env-appengine-vm.sh )"
fi

DBG_AGENT="$( /opt/cdbg/format-env-appengine-vm.sh )"
if [[ -n "$DBG_ENABLE" ]]; then
  if [[ "$GAE_PARTITION" = "dev" ]]; then
    echo "Running locally and DBG_ENABLE is set, enabling standard Java debugger agent"
    DBG_PORT=${DBG_PORT:-5005}
    DBG_AGENT="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DBG_PORT}"
  fi
fi

PROF_AGENT=
if [[ -n "${CPROF_ENABLE}" ]]; then
  if [[ "$GAE_PARTITION" = "dev" ]]; then
    PROF_AGENT=
  else
    PROF_AGENT="$( /opt/cprof/format-env-appengine-vm.sh )"
  fi
fi

SET_TMP=
if [[ -n "${TMPDIR}" ]]; then
  SET_TMP="-Djava.io.tmpdir=$TMPDIR"
fi

