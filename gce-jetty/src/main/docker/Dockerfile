# Copyright 2014 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM gce-debian-openjdk8 

# create jetty group and user
RUN groupadd -r jetty && useradd -r -g jetty jetty

# Create Jetty Home
ENV JETTY_HOME /usr/local/jetty
ENV PATH $JETTY_HOME/bin:$PATH
ADD jetty-distribution.tar.gz /usr/local/
RUN mv /usr/local/jetty-distribution-* $JETTY_HOME && chown -R jetty:jetty $JETTY_HOME

# Create Jetty Base
ENV JETTY_BASE /var/lib/jetty
RUN mkdir -p $JETTY_BASE
WORKDIR $JETTY_BASE
RUN java -jar $JETTY_HOME/start.jar --add-to-startd=http,deploy,jsp,jstl,setuid && \
    chown -R jetty:jetty $JETTY_BASE && \
    mkdir /tmp/jetty && chown -R jetty:jetty /tmp/jetty

RUN ln -s $JETTY_BASE/webapps/root /app
WORKDIR /app

# Start Jetty directly
EXPOSE 8080
CMD []
ENTRYPOINT ["java","-Djava.io.tmpdir=/tmp/jetty","-Djetty.base=/var/lib/jetty","-jar","/usr/local/jetty/start.jar"]
