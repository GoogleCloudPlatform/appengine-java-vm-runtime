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

FROM openjdk8:${docker.label.prefix}8-jre

# create jetty group and user
RUN groupadd -r jetty \
 && useradd -r -g jetty jetty

# Create log directory
RUN mkdir /var/log/app_engine \
 && chgrp jetty /var/log/app_engine \
 && chmod g+rw /var/log/app_engine

# Create Jetty Home
ENV JETTY_HOME /usr/local/jetty
ENV PATH $JETTY_HOME/bin:$PATH
ENV TMPDIR /tmp/jetty
ADD jetty-distribution.tar.gz /usr/local/
RUN mv /usr/local/jetty-distribution-* $JETTY_HOME \
 && chown -R jetty:jetty $JETTY_HOME

# Create Jetty Base
ENV JETTY_BASE /var/lib/jetty
ADD jetty-base $JETTY_BASE
WORKDIR $JETTY_BASE
RUN java -jar $JETTY_HOME/start.jar --add-to-startd=setuid \
 && mkdir $JETTY_BASE/work
 && ln -s $JETTY_BASE/work/jetty-0.0.0.0-8080-root.war-_-any- $JETTY_BASE/root-app
 && chown -R jetty:jetty $JETTY_BASE \
 && mkdir /tmp/jetty && chown -R jetty:jetty /tmp/jetty \
 && ln -s $JETTY_BASE/webapps/root /app

# Start Jetty directly
COPY docker-entrypoint.bash /
RUN chmod +x /docker-entrypoint.bash

EXPOSE 8080
CMD ["java","-Djetty.base=/var/lib/jetty","-jar","/usr/local/jetty/start.jar"]
