#!/bin/bash

isTrue() {  
  if [[ ${1,,*} = "true" ]] ;then 
    return ${true}
  else 
    return ${false}
  fi 
}

ALPN_BOOT=
if isTrue "${ALPN_ENABLE:=false}" ; then
  ALPN_BOOT="$( /opt/alpn/format-env-appengine-vm.sh )"
fi

DBG_AGENT=
if isTrue "${DBG_ENABLE:=$( if [[ -z ${CDBG_DISABLE} ]] ; then echo true; else echo false ; fi )}" ; then
  if [[ "$GAE_PARTITION" = "dev" ]]; then
    echo "Running locally and DBG_ENABLE is set, enabling standard Java debugger agent"
    DBG_AGENT="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DBG_PORT:=5005}"
  else
    unset CDBG_DISABLE
    DBG_AGENT="$( RUNTIME_DIR=$JETTY_BASE /opt/cdbg/format-env-appengine-vm.sh )"
  fi
fi

SET_TMP=
if [[ -n "${TMPDIR}" ]]; then
  SET_TMP="-Djava.io.tmpdir=$TMPDIR"
fi

