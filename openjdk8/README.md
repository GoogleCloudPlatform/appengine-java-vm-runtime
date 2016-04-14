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

If the default command (java) is used, then the entry point sources the [setup-env.bash](https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime/blob/master/openjdk8/src/main/docker/setup-env.bash), which looks for supported features: ALPN, Cloud Debugger & Cloud Profiler.  Each of these features must be explicitly enabled and not disable by environment variables, and each has a script that is run to determine the required JVM arguments:

| Feature        | directory    | Enable            | Disable        | JVM args      |
|----------------|--------------|-------------------|----------------|---------------|
| ALPN           | /opt/alpn/   | $ALPN_ENABLE      | $ALPN_DISABLE  | $ALPN_BOOT    |
| Cloud Debugger | /opt/cdbg/   | \<on by default\> | $CDBG_DISABLE  | $DBG_AGENT    |
| Cloud Profile  | /opt/cprof/  | $CPROF_ENABLE     | $CPROF_DISABLE | $PROF_AGENT   |
| Temporary file |              | $TMPDIR           |                | $SET_TMP      |
| Java options   |              | $JAVA_OPTS        |                | $JAVA_OPTS    |

The command line executed is effectively (where $@ are the args passed into the 
docker entry point):
```
java $ALPN_BOOT $DBG_AGENT $PROF_AGENT $SET_TMP $JAVA_OPTS "$@"
```





