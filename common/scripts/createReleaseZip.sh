#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

rm -rdf ~/.m2/repository/org/graalvm/buildtools

cp version.properties version.properties.bak

sed -i 's/-SNAPSHOT//' version.properties
common/scripts/updateVersions.sh
common/scripts/buildAll.sh
common/scripts/testAll.sh

mv version.properties.bak version.properties
common/scripts/updateVersions.sh

mkdir -p build
cd build
location=$(pwd)
rm -f artifacts.zip

cd ~/.m2/repository
zip -r $location/artifacts.zip org/graalvm/buildtools
popd
