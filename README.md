appengine-java-vm-runtime
=========================

Complete code source of the Google App Engine [Java Managed VM](https://cloud.google.com/appengine/docs/managed-vms/) Docker image.
It has 2 Java libraries, one generic for default servlets, filters and App Engine management, and one which is Jetty 9.x specific for Session management, App Engine APIs hook, and user login.

The dependent Java libraries are build, and used by the [appengine-jetty-managed-runtime/src/main/docker/Dockerfile](appengine-jetty-managed-runtime/src/main/docker/Dockerfile) that build the Jetty9 Java8 GAE Compatibility image. To use
the image, you need to build it with either a local docker installation or environment variables pointing to a remote docker
instance:

      mvn clean install

This will create the following docker images:
 * gke-debian-openjdk:8-jre
 * gke-jetty:9.3.x
 * appengine-mvn-opensource:1.9.x

The last of these images may be used as the basis for a Java Web Application Archive: put a Dockerfile at the top directory (for example, with a Maven build, create the Dockerfile in ./src/main/webapp directory) and from this Docker image, just add your Web Application content into the /app of the container.

      FROM appengine-mvn-opensource:latest
      ADD . /app

Then, you can run this App Engine Managed VM container via the Cloud SDK [https://cloud.google.com/appengine/docs/java/managed-vms/](https://cloud.google.com/appengine/docs/java/managed-vms/)

Enjoy...
