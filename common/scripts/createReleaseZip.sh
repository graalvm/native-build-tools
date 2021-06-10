#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

rm -rdf ~/.m2/repository/org/graalvm/buildtools

set +e
git commit -am "tmp"
set -e

sed -i 's/-SNAPSHOT//' version.properties
common/scripts/updateVersions.sh
common/scripts/buildAll.sh
common/scripts/testAll.sh

set +e
git checkout --
git reset --hard
set -e

mkdir -p build
cd build
location=$(pwd)
rm -f artifacts.zip

cd ~/.m2/repository
zip -r $location/artifacts.zip org/graalvm/buildtools
popd
