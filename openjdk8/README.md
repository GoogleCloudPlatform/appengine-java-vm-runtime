# openjdk8 Docker Image

This project builds a Docker image for debian and openjdk8 is used as a base image for Google Container Engine and 
Google App Engine [Java Managed VM](https://cloud.google.com/appengine/docs/managed-vms/) Docker images.

## Building the image
To build the image you need git, docker and maven installed:
```
$ git clone https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime.git
$ cd appengine-java-vm-runtime/openjdk8
$ mvn clean install
```
The resulting image is called openjdk8:8-jre 

## The Default Entry Point
The default entrypoint will print the JDK version:
```
$ docker run openjdk8:8-jre
openjdk version "1.8.0_66-internal"
OpenJDK Runtime Environment (build 1.8.0_66-internal-b17)
OpenJDK 64-Bit Server VM (build 25.66-b17, mixed mode)
```

Any arguments passed to the entry point that are not executable are treated as arguments to the java command:
```
$ docker run openjdk8:8-jre -jar /usr/share/someapplication.jar
```

Any arguments passed to the entry point that are executable replace the default command, thus a shell could
be run with:
```
> docker run -it --rm openjdk8:8-jre bash
root@c7b35e88ff93:/# 
```

## Entry Point Features
The entry point for the openjdk8 image is [docker-entrypoint.bash](https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime/blob/master/openjdk8/src/main/docker/docker-entrypoint.bash), which does the processing of the passed command line arguments to look for an executable alternative or arguments to the default command (java).

If the default command (java) is used, then the entry point sources the [setup-env.bash](https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime/blob/master/openjdk8/src/main/docker/setup-env.bash), which looks for supported features to be enabled and/or configured.  The following table indicates the environment variables that may be used to enable/disable/configure features, any default values if they are not set: 

|Env Var           | Description         | Type     | Default                               |
|------------------|---------------------|----------|---------------------------------------|
|`DBG_ENABLE`      | Stackdriver Debugger| boolean  | `true`                                |
|`TMPDIR`          | Temporary Directory | dirname  |                                       |
|`JAVA_TMP_OPTS`   | JVM tmpdir args     | JVM args | `-Djava.io.tmpdir=${TMPDIR}`          |
|`HEAP_SIZE`       | Available heap      | size     | Derived from `/proc/meminfo`          |
|`JAVA_HEAP_OPTS`  | JVM heap args       | JVM args | `-Xms${HEAP_SIZE} -Xmx${HEAP_SIZE}`   |
|`JAVA_GC_OPTS`    | JVM GC args         | JVM args | `-XX:+UseG1GC` plus configuration     |
|`JAVA_GC_LOG`     | JVM GC log file     | filename |                                       |
|`JAVA_GC_LOG_OPTS`| JVM GC args         | JVM args | Derived from `$JAVA_GC_LOG`           |
|`JAVA_USER_OPTS`  | JVM other args      | JVM args |                                       |
|`JAVA_OPTS`       | JVM args            | JVM args | See below                             |

If not explicitly set, `JAVA_OPTS` is defaulted to 
```
JAVA_OPTS:=-showversion \
           ${JAVA_TMP_OPTS} \
           ${DBG_AGENT} \
           ${JAVA_HEAP_OPTS} \
           ${JAVA_GC_OPTS} \
           ${JAVA_GC_LOG_OPTS} \
           ${JAVA_USER_OPTS}
```

The command line executed is effectively (where $@ are the args passed into the docker entry point):
```
java $JAVA_OPTS "$@"
```





