#!/bin/bash

isTrue() {  
  if [[ ${1,,*} = "true" ]] ;then 
    return ${true}
  else 
    return ${false}
  fi 
}

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

# Setup default Java Options
: ${JAVA_TMP_OPTS:=$( if [[ -z ${TMPDIR} ]]; then echo ""; else echo "-Djava.io.tmpdir=$TMPDIR"; fi)}
: ${HEAP_SIZE:=$(awk -v frac=${HEAP_SIZE_FRAC:=0.8} -v res=${RAM_RESERVED_MB:=400} /MemTotal/'{ print int($2/1024*frac-res) "M" } ' /proc/meminfo)}
: ${JAVA_HEAP_OPTS:=-Xms${HEAP_SIZE} -Xmx${HEAP_SIZE}}
: ${JAVA_GC_OPTS:=-XX:+PrintCommandLineFlags -XX:+UseG1GC -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+ParallelRefProcEnabled}
: ${JAVA_GC_LOG_OPTS:=$( if [[ -z ${JAVA_GC_LOG} ]]; then echo ""; else echo "-Xloggc:${JAVA_GC_LOG} -XX:+UseGCLogFileRotation -XX:GCLogFileSize=1048576 -XX:NumberOfGCLogFiles=4"; fi)}
: ${JAVA_OPTS:=-showversion ${JAVA_TMP_OPTS} ${DBG_AGENT} ${JAVA_HEAP_OPTS} ${JAVA_GC_OPTS} ${JAVA_GC_LOG_OPTS} ${JAVA_USER_OPTS}}



