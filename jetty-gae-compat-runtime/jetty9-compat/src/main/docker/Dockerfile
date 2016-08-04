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

FROM jetty9:${docker.label.prefix}${jetty.version}

# Create env vars to identify image
ENV GAE_IMAGE_NAME ${project.artifactId}
ENV GAE_IMAGE_LABEL ${docker.label.prefix}${project.version}
ENV GAE_IMAGE_VERSION ${project.version}

# Remove stock Jetty Base
RUN rm -fr $JETTY_BASE \
 && mkdir $JETTY_BASE  

# add jetty9 compat base
ADD jetty-base jetty_run.sh $JETTY_BASE/ 

WORKDIR $JETTY_BASE
RUN sed -i 's/^\([a-zA-Z\.]*=\).*/#\1(see gae.ini)/' start.d/server.ini \
 && java -jar $JETTY_HOME/start.jar --dry-run \
  | sed 's/^.*java /exec & ${JAVA_OPTS} /' > jetty_cmd.sh \
 && chown -R jetty:jetty $JETTY_BASE \
 && chmod +x $JETTY_BASE/jetty_run.sh
    
WORKDIR /app

# Jetty HTTP port number
EXPOSE 8080
# Java VM debug port number
EXPOSE 5005

# Env variable used by the jetty_run.sh script
ENV RUNTIME_DIR /var/lib/jetty

# Clean any CMD that might be inherited from previous image, because that
# will pollute our ENTRYPOINT, see
# http://docs.docker.io/en/latest/reference/builder/#entrypoint.
CMD []
VOLUME ["/var/log/app_engine"] 
ENTRYPOINT ["/var/lib/jetty/jetty_run.sh"]
