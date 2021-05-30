#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

pushd common/junit-platform-native
./gradlew clean publishToMavenLocal
popd

pushd native-image-maven-plugin
mvn clean install
popd

pushd native-image-gradle-plugin
./gradlew clean publishToMavenLocal
popd
