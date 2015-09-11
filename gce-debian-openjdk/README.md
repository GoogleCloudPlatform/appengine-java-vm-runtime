gce-debian-openjdk8
=========================

This project builds a Docker image for debian and openjdk8 is used as a base image for Google Computer Engine and 
Google App Engine [Java Managed VM](https://cloud.google.com/appengine/docs/managed-vms/) Docker images.

To use the image, you need to build it:

       git clone https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime.git
       cd appengine-java-vm-runtime/gce-debian-openjdk8
       mvn clean install

The resulting image is called gce-debian-openjdk8 and the default bash entrypoint can be run with

       docker run -it --rm gce-debian-openjdk8

Enjoy...
