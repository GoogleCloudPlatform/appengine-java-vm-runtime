
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
     
     