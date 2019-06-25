#!/bin/bash

set -e
set -u
set -o pipefail

if [[ -z "$(which shasum)" ]]; then
	echo "Please install shasum before running this script, ensure it is in the PATH to able to invoke it."
	return -1
fi

archiveFilename=$1
DEFAULT_SHA_FILENAME="${1}.sha256sum.txt"
shaSumFilename=${2:-${DEFAULT_SHA_FILENAME}}

echo ">>>> Creating a sha256 hash from ${archiveFilename}"
set -x
shasum -a 256 ${archiveFilename} > ${shaSumFilename}
set +x
cat ${shaSumFilename} | awk '{print $1}' > ${shaSumFilename}.tmp
mv ${shaSumFilename}.tmp ${shaSumFilename}
echo ""
echo "${shaSumFilename} created, containing SHA:"
cat ${shaSumFilename}