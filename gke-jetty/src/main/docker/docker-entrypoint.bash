#!/bin/bash

START_JETTY="-Djetty.base=$JETTY_BASE -jar $JETTY_HOME/start.jar"

if [ "java" = "$1" -o "$(which java)" = "$1" ] ; then
  shift
  START_JETTY=
fi

if ! type "$1" &>/dev/null; then
  source /gke-env.bash
  set -- java $JAVA_OPTS $ALPN_BOOT $DBG_AGENT $PROF_AGENT $SET_TMP $START_JETTY "$@"
fi

exec "$@"


