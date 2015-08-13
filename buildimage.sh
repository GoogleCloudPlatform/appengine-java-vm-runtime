#!/bin/sh
mvn clean install

rm -rf docker/jetty
mkdir -p docker/jetty
tar -C docker/jetty --strip-components=1 -zxf appengine-jetty-managed-runtime/target/jetty-distribution.tar.gz

rm -rf docker/lib/ext
mkdir -p docker/lib/ext
cp appengine-jetty-managed-runtime/target/appengine-jetty-managed-runtime-*.jar docker/lib/ext/appengine-jetty-managed-runtime.jar
cp appengine-managed-runtime/target/appengine-managed-runtime-*.jar docker/lib/ext/appengine-managed-runtime.jar
cp appengine-managed-runtime/target/appengine-api-1.0-sdk-*.jar docker/lib/ext/appengine-api-1.0-sdk.jar
cd docker
output=${1:-appengine-mvn-opensource}
echo using $output as the image name [you can overwrite it by passing the desired name in the command]
docker build -t ${output} .


