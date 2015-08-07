appengine-java-vm-runtime
=========================

Complete code source of the Google App Engine [Java Managed VM](https://cloud.google.com/appengine/docs/managed-vms/) Docker image.
It has 2 Java libraries, one generic for default servlets, filters and App Engine management, and one which is Jetty 9.x specific for Session management, App Engine APIs hook, and user login.

The Java libraries are pushed to Maven Central, and used by the [docker/Dockerfile](docker/Dockerfile) that build the image. To use the image, you need to build it:

       git clone https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime.git
       cd appengine-java-vm-runtime
       mvn clean install
       mkdir -p docker/lib/ext
       cp appengine-jetty-managed-runtime/target/appengine-jetty-managed-runtime*.jar docker/lib/ext/appengine-jetty-managed-runtime.jar
       cp appengine-managed-runtime/target/appengine-managed-runtime*.jar docker/lib/ext/appengine-managed-runtime.jar
       cd docker
       docker build -t myimage .

Then, for a Java Web Application Archive, put a Dockerfile at the top directory (for example, with a Maven build, create the Dockerfile in ./src/main/webapp directory) and from this Docker image, just add your Web Application content into the /app of the container.

      FROM myimage
      ADD . /app

Then, you can run this App Engine Managed VM container via the Cloud SDK [https://cloud.google.com/appengine/docs/java/managed-vms/](https://cloud.google.com/appengine/docs/java/managed-vms/)

Enjoy...
