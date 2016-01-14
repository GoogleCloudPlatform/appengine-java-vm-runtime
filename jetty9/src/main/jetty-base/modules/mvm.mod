#
# MVM Module
#

[depend]
resources
server

[optional]

[xml]
etc/mvm.xml

[lib]
lib/mvm/*.jar

[ini-template]

## Google AppEngine Defaults
jetty.httpConfig.outputAggregationSize=32768
jetty.httpConfig.headerCacheSize=512

jetty.httpConfig.sendServerVersion=true
jetty.httpConfig.sendDateHeader=false

