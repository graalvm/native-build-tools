#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

pushd samples/java-application-with-tests
# The include build part is only needed if you want to use the version under development
# otherwise update the samples to use a release version
gradle run nativeRun test nativeTest --include-build ../../native-gradle-plugin
popd

pushd samples/java-application-with-tests
mvn compile exec:java@java
mvn -Pnative test package exec:exec@native
popd
