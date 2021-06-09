#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

pushd examples/gradle
./gradlew run nativeRun test nativeTest
popd

pushd examples/maven
mvn compile exec:java@java
mvn -Pnative test package exec:exec@native
popd
