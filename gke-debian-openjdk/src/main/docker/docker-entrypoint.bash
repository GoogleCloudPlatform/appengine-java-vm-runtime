#!/bin/bash

if [ "java" = "$1" -o "$(which java)" = "$1" ] ; then
  shift
fi

if ! type "$1" &>/dev/null; then
  source /gke-env.bash
  set -- java $JAVA_OPTS $ALPN_BOOT $DBG_AGENT $PROF_AGENT $SET_TMP "$@"
fi

exec "$@"




