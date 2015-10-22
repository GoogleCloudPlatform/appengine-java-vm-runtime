#!/bin/bash

if ! type "$1" &>/dev/null; then
  ALPN_BOOT=
  if [[ -n "$ALPN_ENABLE" ]]; then
    ALPN_BOOT="$( /opt/alpn/format-env-appengine-vm.sh )"
  fi

  DBG_AGENT=
  if [[ -n "$DBG_ENABLE" ]]; then
    if [[ "$GAE_PARTITION" = "dev" ]]; then
      DBG_AGENT="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DBG_PORT:-5005}"
    else
      DBG_AGENT="$( /home/vmagent/cdbg/format-env-appengine-vm.sh )"
    fi
  fi

  PROF_AGENT=
  if [[ -n "${CPROF_ENABLE}" ]]; then
    if [[ "$GAE_PARTITION" = "dev" ]]; then
      PROF_AGENT=
    else
      PROF_AGENT="$( /home/vmagent/cprof/format-env-appengine-vm.sh )"
    fi
  fi

  SET_TMP=
  if [[ -n "${TMPDIR}" ]]; then
    SET_TMP="-Djava.io.tmpdir=$TMPDIR"
  fi

  set -- java $ALPN_BOOT $DBG_AGENT $PROF_AGENT $SET_TMP "$@"
fi

exec "$@"




