#
# GAE Module for Jetty 9 MVM Image
#

[depend]
resources
server

[optional]

[xml]
etc/gae.xml

[lib]
lib/gae/*.jar

[ini-template]

## Google AppEngine Defaults
jetty.httpConfig.outputAggregationSize=32768
jetty.httpConfig.headerCacheSize=512

jetty.httpConfig.sendServerVersion=true
jetty.httpConfig.sendDateHeader=false

#gae.httpPort=80
#gae.httpsPort=443

#jetty.server.stopTimeout=30000
