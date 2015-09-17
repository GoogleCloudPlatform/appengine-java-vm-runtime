#!/bin/bash

if ! type "$1" &>/dev/null; then
	set -- java -jar "-Djava.io.tmpdir=$TMPDIR" "-Djetty.base=$JETTY_BASE" "$JETTY_HOME/start.jar" "$@"
fi

exec "$@"

