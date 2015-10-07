#!/bin/bash

if ! type "$1" &>/dev/null; then
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

    set -- java $DBG_AGENT $PROF_AGENT -jar "-Djava.io.tmpdir=$TMPDIR" "-Djetty.base=$JETTY_BASE" "$JETTY_HOME/start.jar" "$@"
fi

exec "$@"




