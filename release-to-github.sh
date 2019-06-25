#!/bin/bash

set -e
set -u
set -o pipefail

TARGET_REPO="MeterianHQ/bitbucket-pipelines-cli"

if [[ -z ${METERIAN_GITHUB_TOKEN} ]]; then
  echo "METERIAN_GITHUB_TOKEN cannot be found in the current environment, please populate to proceed either in the startup bash script of your OS or in the environment variable settings of your CI/CD interface."
  exit -1
fi

RELEASE_VERSION="$(cat version.txt)"
TAG_NAME="v$(cat version.txt)"

echo "We will be reading version info from the version.txt file, to construct the tagname, please ensure it is the most actual."
echo "Current TAG_NAME=${TAG_NAME}"

POST_DATA=$(printf '{
  "tag_name": "%s",
  "target_commitish": "master",
  "name": "%s",
  "body": "Release %s",
  "draft": false,
  "prerelease": false
}' ${TAG_NAME} ${TAG_NAME} ${TAG_NAME})
echo "Creating release ${RELEASE_VERSION}: $POST_DATA"
curl \
    -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/vnd.github.v3+json" \
    -X POST -d "${POST_DATA}" "https://api.github.com/repos/${TARGET_REPO}/releases"

mkdir -p artifacts
CURL_OUTPUT="./artifacts/github-release.listing"
echo "Getting Github ReleaseId"
curl \
    -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3+json" \
    -X GET "https://api.github.com/repos/${TARGET_REPO}/releases/tags/${TAG_NAME}"
    tee ${CURL_OUTPUT}
RELEASE_ID=$(cat ${CURL_OUTPUT} | grep id | head -n 1 | tr -d " " | tr "," ":" | cut -d ":" -f 2)

function uploadAsset() {
    local releaseId=$1
    local assetName=$2
    local releaseArtifact="${assetName}"
    echo "Uploading asset to ReleaseId ${releaseId}, name=${assetName}"
    curl \
        -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
        -H "Content-Type: application/zip" \
        -H "Accept: application/vnd.github.v3+json" \
        --data-binary @${releaseArtifact} \
         "https://uploads.github.com/repos/${TARGET_REPO}/releases/${releaseId}/assets?name=${assetName}"
}

TARGET_JAR_FILE="${1:-bitbucket-pipelines-cli-jar-with-dependencies.jar}"
uploadAsset ${RELEASE_ID} "${TARGET_JAR_FILE}"
TARGET_JAR_CHECKSUM_FILE="${TARGET_JAR_FILE}.sha256sum.txt"
uploadAsset ${RELEASE_ID} "${TARGET_JAR_CHECKSUM_FILE}"

echo "Finished uploading to GitHub"
echo ""
echo "Checkout curl output at ${CURL_OUTPUT}"
echo ""
echo "Use curl -O -L [github release url] to download this artifacts."
echo "    for e.g."
echo "        curl -O -L https://github.com/${TARGET_REPO}/releases/download/${TAG_NAME}/${TARGET_JAR_FILE}"
echo "Download the checksum of the artifact"
echo "        curl -O -L https://github.com/${TARGET_REPO}/releases/download/${TAG_NAME}/${TARGET_JAR_CHECKSUM_FILE}"