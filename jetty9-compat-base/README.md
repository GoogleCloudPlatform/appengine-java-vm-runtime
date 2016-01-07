jetty9-compat-base
==================

This module builds a [jetty base](https://www.eclipse.org/jetty/documentation/current/startup-base-and-home.html) directory 
that contains the Google App Engine runtime environment. This base is used by the docker image built to run the 
Google App Engine on Google Container Engine in compatibility mode.

The base can also be run directly for local testing with:
```shell
java -Droot.webapp=../webapps/testwebapp/ \
     -Dcom.google.apphosting.logs=./logs \
     -jar ../jetty-distribution-9.3.5.v20151012/start.jar \
     --module=testMetadataServer
```
The command uses the system property `root.webapp` to point to an exploded WAR file suitable for GAE deployment (default `webapps/root`); 
the system property `com.google.apphosting.logs` to configure a directory to log into (default /var/log/appi\_engine);
the normal jetty start.jar;
A testMetadataServer module that locally handles GAE rest calls 

## Artefacts

This module builds 3 maven artefacts:
 * jetty9-compat-base-@VERSION@.jar - The classes built in this module that addapt the generic appengine-managed-runtime module to the Jetty9 container.
 * jetty9-compat-base-@VERSION@.tar.gz - A tar archive of the jar file deployed with all it's dependencies as a runnable [jetty base](https://www.eclipse.org/jetty/documentation/current/startup-base-and-home.html). 
 * jetty9-compat-base-@VERSION@.zip - A zip archive of the jar file deployed with all it's dependencies as a runnable [jetty base](https://www.eclipse.org/jetty/documentation/current/startup-base-and-home.html). 
Enjoy...
