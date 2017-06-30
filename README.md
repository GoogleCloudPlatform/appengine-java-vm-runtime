[![deprecated](http://badges.github.io/stability-badges/dist/deprecated.svg)](http://github.com/badges/stability-badges)

We've moved!
=====================
Note that the maintenance of OpenJDK and the Jetty Docker images has moved to new GitHub repositories:
 * [openjdk-runtime](https://github.com/GoogleCloudPlatform/openjdk-runtime)
 * [jetty-runtime](https://github.com/GoogleCloudPlatform/jetty-runtime)

appengine-java-vm-runtime
=========================

Complete code source of the Google App Engine [flexible environment](https://cloud.google.com/appengine/docs/flexible/) Docker image.
It has 2 Java libraries, one generic for default servlets, filters and App Engine management, and one which is Jetty 9.x specific for Session management, App Engine APIs hook, and user login.

The dependent Java libraries are build, and used by the [appengine-jetty-managed-runtime/src/main/docker/Dockerfile](appengine-jetty-managed-runtime/src/main/docker/Dockerfile) that build the Jetty9 Java8 GAE Compatibility image. To use
the image, you need to build it with either a local docker installation or environment variables pointing to a remote docker
instance:
```bash
      mvn clean install
```

This will create the following docker images:
 * gke-debian-openjdk:8-jre
 * gke-jetty:9.3.x
 * appengine-mvn-opensource:1.9.x

The last of these images may be used as the basis for a Java Web Application Archive: put a Dockerfile at the top directory (for example, with a Maven build, create the Dockerfile in ./src/main/webapp directory) and from this Docker image, just add your Web Application content into the /app of the container:

```Dockerfile
FROM appengine-mvn-opensource:latest
ADD . /app
```

The `Dockerfile` may also be used to update the jetty configuration, for example the following will enable
the gzip module:
```Dockerfile
WORKDIR $JETTY_BASE
RUN java -jar $JETTY_HOME/start.jar --approve-all-licenses --add-to-startd=gzip
```

If a custom image changes the Jetty configuration, then the Dockerfile must regenerate the `jetty_cmd.sh` script:
```Dockerfile
RUN java -jar $JETTY_HOME/start.jar --dry-run | \
    sed 's/^.*java /exec & ${ALPN_BOOT} ${DBG_AGENT} ${PROF_AGENT} \
    -Xms${HEAP_SIZE} -Xmx${HEAP_SIZE} ${JAVA_OPTS} /' > jetty_cmd.sh
```

Then, you can run this App Engine flexible environment container via the Cloud SDK
https://cloud.google.com/appengine/docs/flexible/java/hello-world

Enjoy...

## Troubleshooting

If using Docker for Mac, make sure to expose the docker socket to maven:

      export DOCKER_HOST=unix:///var/run/docker.sock

See https://docs.docker.com/docker-for-mac/troubleshoot/#/known-issues
