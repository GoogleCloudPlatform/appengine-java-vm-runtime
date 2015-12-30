#!/bin/bash

# default jetty arguments
DEFAULT_ARGS="-Djetty.base=$JETTY_BASE -jar $JETTY_HOME/start.jar"

# If the passed arguments start with the java command
if [ "java" = "$1" -o "$(which java)" = "$1" ] ; then
  # ignore the java command as it is the default
  shift
  # clear the default args, use the passed args
  DEFAULT_ARGS=
fi

# If the first argument is not executable
if ! type "$1" &>/dev/null; then
  # then treat all arguments as arguments to the java command
  
  # source the supported feature JVM arguments
  source /gke-env.bash
  
  # set the command line to java with the feature arguments and passed arguments
  set -- java $ALPN_BOOT $DBG_AGENT $PROF_AGENT $SET_TMP $JAVA_OPTS $DEFAULT_ARGS "$@"
fi

exec "$@"


