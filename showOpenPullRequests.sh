#!/bin/bash

set -e
set -u
set -o pipefail

userName=meterian-bot
repoName=ClientOfMutabilityDetector
state=OPEN
URI="https://api.bitbucket.org/2.0/repositories/${userName}/${repoName}/pullrequests?state=${state}"

if [[ -z "$(which jq)" ]]; then
	echo "Please install jq before running this script, ensure it is in the PATH to able to invoke it."
	return 0
fi

set -x
curl ${URI} | jq | less
set +x