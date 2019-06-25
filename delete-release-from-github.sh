#!/bin/bash

set -e
set -u
set -o pipefail

TARGET_REPO="MeterianHQ/bitbucket-pipelines-cli"
RELEASE_VERSION="$(cat version.txt)"
TAG_NAME="v$(cat version.txt)"

echo ""
echo "~~~~ Fetching Release ID for ${TAG_NAME}"
CURL_OUTPUT="./artifacts/github-release.listing"
curl \
    -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3+json" \
    -X GET "https://api.github.com/repos/${TARGET_REPO}/releases/tags/${TAG_NAME}" |
    tee ${CURL_OUTPUT}
RELEASE_ID=$(cat ${CURL_OUTPUT} | grep id | head -n 1 | tr -d " " | tr "," ":" | cut -d ":" -f 2)

echo ""
echo "~~~~ Deleting release with ID ${RELEASE_ID} linked to ${TAG_NAME}"
curl \
    -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3+json" \
    -X DELETE "https://api.github.com/repos/${TARGET_REPO}/releases/${RELEASE_ID}"

echo ""
echo "~~~~ Deleting reference refs/tags/${TAG_NAME}"
curl \
    -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3+json" \
    -X DELETE  "https://api.github.com/repos/${TARGET_REPO}/git/refs/tags/${TAG_NAME}"