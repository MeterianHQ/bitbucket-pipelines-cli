#!/bin/bash

set -e
set -u
set -o pipefail

docker run -it                      \
           --workdir /home/         \
           --volume $(pwd)/:/home/  \
           bitbucket-pipelines-cli  \
           /bin/bash