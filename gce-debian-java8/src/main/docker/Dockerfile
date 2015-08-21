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

FROM google/debian:wheezy
ENV DEBIAN_FRONTEND noninteractive

RUN echo  "deb http://ftp.us.debian.org/debian sid main" >> /etc/apt/sources.list
RUN apt-get -q update
RUN apt-get -y -q --no-install-recommends install openjdk-8-jre ca-certificates netbase
RUN apt-get clean
RUN rm /var/lib/apt/lists/*_*

# workaround for https://bugs.debian.org/775775
RUN /var/lib/dpkg/info/ca-certificates-java.postinst configure

CMD []
ENTRYPOINT ["bash"]
