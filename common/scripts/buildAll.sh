#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

# We're pushing to Maven Local so that the user can try out samples directly
# But we're using the "common repository" for the build outcome

./gradlew clean --no-parallel
./gradlew publishToMavenLocal publishAllPublicationsToCommonRepository

