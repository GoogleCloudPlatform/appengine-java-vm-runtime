<<<<<<< HEAD

Jetty9 (using OpenJDK 8) Google Conpute Engine Docker image
=========================

Base Jetty 9.3 image using Open JDK 8 and following the setting guidelines defined
at [https://github.com/docker-library/docs/tree/master/jetty#configuration](https://github.com/docker-library/docs/tree/master/jetty#configuration).

This image works with App Engine Managed VMa as a custom runtime.
In order to use it, you need to build the image (let's call it `YOUR_BUILT_IMAGE`), (and optionally push it to a Docker registery like gcr.io). Then, you can add to any pure Java EE 7 Web Application projects these 2 configuration files next to the exploded WAR directory:

`Dockerfile` file would be:
      
      FROM YOUR_BUILT_IMAGE
      add . /app
      
That will add the Web App Archive directory in the correct location for the Docker container.

Then, an `app.yaml` file to configure the GAE Managed VM product:

      runtime: custom
      vm: true
      api_version: 1
      
Once you have this configuration, you can use the Google Cloud SDK to deploy this directory containing the 2 configuration files and the Web App directory using:

     gcloud preview app deploy app.yaml
     
     
=======
gce-jetty
=========

This project builds a Docker Image for 
Google Compueter Engine [Java Managed VM](https://cloud.google.com/appengine/docs/managed-vms/)
that provides the Jetty 9.3 Servet container on top of the gce-debian-openjdk8 image.

The layout of this image is intended to mostly mimic the official 
[docker-jetty](https://github.com/appropriate/docker-jetty) image and unless otherwise noted,
the official [docker-jetty documentation](https://github.com/docker-library/docs/tree/master/jetty)
should apply.

## Building the Jetty image
To use the image, you need to build it:
```console
git clone https://github.com/GoogleCloudPlatform/appengine-java-vm-runtime.git
cd appengine-java-vm-runtime/gce-jetty
mvn clean install
```

## Running the Jetty image
The resulting image is called gce-jetty-9.3 and can be run with:
```console
docker run gce-jetty-9.3
```

## Configuring the Jetty image
Arguments passed to the docker run command are passed to Jetty, so the 
configuration of the jetty server can be seen with a command like:
```console
docker run gce-jetty-9.3 --list-config
```

Alternate commands can also be passed to the docker run command, so the
image can be explored with 
```console
docker run t --rm gce-jetty-9.3 bash
```

To update the server configuration in a derived Docker image, the `Dockerfile` may
enable additional modules with `RUN` commands like:
```
WORKDIR $JETTY_BASE
RUN java -jar "$JETTY_HOME/start.jar" --add-to-startd=jmx,stats
```
Modules may be configured in a `Dockerfile` by editing the properties in the corresponding `/var/lib/jetty/start.d/*.mod` file or the module can be deactivated by removing that file.


Enjoy...
>>>>>>> GoogleCloudPlatform/jetty93
