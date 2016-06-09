#!/bin/bash

# default jetty arguments
DEFAULT_ARGS="-Djetty.base=$JETTY_BASE -jar $JETTY_HOME/start.jar"

# Unpack a WAR app (if present) beforehand so that Stackdriver Debugger
# can load it. This should be done before the JVM for Jetty starts up.
WEBAPP_WAR=$JETTY_BASE/webapps/root.war
WEBAPP_ROOT=$JETTY_BASE/webapps/root
if [ -e "$WEBAPP_WAR" ]; then
  # Unpack it only if $WEBAPP_ROOT doesn't exist or empty.
  if [ ! -e "$WEBAPP_ROOT" -o ! "$( ls -A $WEBAPP_ROOT )" ]; then
    unzip $WEBAPP_WAR -d $WEBAPP_ROOT
    chown -R jetty.jetty $WEBAPP_ROOT
  fi
fi

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
  source /setup-env.bash
  
  # set the command line to java with the feature arguments and passed arguments
  set -- java $JAVA_OPTS $DEFAULT_ARGS "$@"
fi

exec "$@"


