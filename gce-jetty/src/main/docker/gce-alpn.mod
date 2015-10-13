# ALPN can be proved by the gce-debian-openjdk image if the ALPN_ENABLE 
# environment variable is set.   
#
# This module is an alternate to the standard jetty alpn.mod that uses
# the gce provided ALPN implementation. It should be explicitly enabled
# prior http2 or any module that depends on alpn.

[name]
alpn

[depend]
ssl

[lib]
lib/jetty-alpn-client-${jetty.version}.jar
lib/jetty-alpn-server-${jetty.version}.jar

[xml]
etc/jetty-alpn.xml

[files]
/opt/alpn/
lib/
lib/alpn/

[ini-template]
## Overrides the order protocols are chosen by the server.
## The default order is that specified by the order of the
## modules declared in start.ini.
# jetty.alpn.protocols=h2-16,http/1.1

## Specifies what protocol to use when negotiation fails.
# jetty.alpn.defaultProtocol=http/1.1

## ALPN debug logging on System.err
# jetty.alpn.debug=false

