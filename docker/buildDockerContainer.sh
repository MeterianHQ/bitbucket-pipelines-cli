#!/bin/bash

set -e
set -u
set -o pipefail

cd ..
docker build                                                                          \
       --build-arg METERIAN_API_TOKEN=${METERIAN_API_TOKEN}                           \
       --build-arg METERIAN_BITBUCKET_USER=${METERIAN_BITBUCKET_USER}                 \
       --build-arg METERIAN_BITBUCKET_APP_PASSWORD=${METERIAN_BITBUCKET_APP_PASSWORD} \
       --build-arg METERIAN_BITBUCKET_EMAIL=${METERIAN_BITBUCKET_EMAIL}               \
       -t bitbucket-pipelines-cli                                                     \
       -f docker/Dockerfile . || (true && cd -)
