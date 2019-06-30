#!/bin/bash

set -e
set -u
set -o pipefail

CURRENT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

TARGET_REPO="MeterianHQ/bitbucket-pipelines-cli"
RELEASE_VERSION="$(cat ${CURRENT_DIR}/version.txt)"
TAG_NAME="v$(cat ${CURRENT_DIR}/version.txt)"

echo ""
echo "~~~~ Fetching Release ID for ${TAG_NAME}"
mkdir -p ${CURRENT_DIR}/artifacts
CURL_OUTPUT="${CURRENT_DIR}/artifacts/github-release.listing"
GET_RELEASE_ACTION=$(curl \
    -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3+json" \
    -X GET "https://api.github.com/repos/${TARGET_REPO}/releases/tags/${TAG_NAME}" | 
    tee ${CURL_OUTPUT} || true)
RELEASE_ID=$(cat ${CURL_OUTPUT} | grep id | head -n 1 | tr -d " " | tr "," ":" | cut -d ":" -f 2 || true)

echo "RELEASE_ID: ${RELEASE_ID}"

echo ""
mkdir -p ${CURRENT_DIR}/artifacts
FETCH_ASSETS_OUTPUT="${CURRENT_DIR}/artifacts/fetch.assets"
if [[ -z "${RELEASE_ID}" ]]; then
	echo "~~~~ No assets available as no release(s) available for ${TAG_NAME}"
	rm ${FETCH_ASSETS_OUTPUT} || true
	echo "" > ${FETCH_ASSETS_OUTPUT}
else
	echo "~~~~ Fetching assets for ${TAG_NAME}"
	ASSETS=$(curl \
	    -H "Authorization: token ${METERIAN_GITHUB_TOKEN}" \
	    -H "Accept: application/vnd.github.v3+json" \
	    -X GET "https://api.github.com/repos/${TARGET_REPO}/releases/${RELEASE_ID}/assets" | 
        tee ${CURL_OUTPUT} || true)
	echo $(grep browser_download_url ${CURRENT_DIR}/artifacts/github-release.listing | grep sha256sum | awk '{print $2}' | tr -d '"' || true) > ${FETCH_ASSETS_OUTPUT}
fi