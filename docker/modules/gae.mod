#
# Jetty HTTP Connector
#

[depend]
http
deploy
annotations
plus
quickstart
jsp
jstl

[xml]
etc/gae.xml

[ini-template]

## Google AppEngine Defaults

## Make the aggregation size the same as the output buffer size
jetty.output.aggregation.size=32786

header.cache.size=512


## Override server.ini
threads.min=10
threads.max=500
threads.timeout=60000
jetty.output.buffer.size=32768
jetty.request.header.size=8192
jetty.response.header.size=8192
jetty.send.server.version=true
jetty.send.date.header=false
#jetty.host=myhost.com
jetty.dump.start=false
jetty.dump.stop=false
jetty.delayDispatchUntilContent=false

