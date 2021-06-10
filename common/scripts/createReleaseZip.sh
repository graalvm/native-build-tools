#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

rm -rdf ~/.m2/repository/org/graalvm/buildtools
git commit -am "tmp"

sed -i 's/-SNAPSHOT//' version.properties
common/scripts/updateVersions.sh
common/scripts/buildAll.sh
common/scripts/testAll.sh

git checkout --
git reset --hard

mkdir -p build
cd build
location=$(pwd)
rm -f artifacts.zip

cd ~/.m2/repository
zip -r $location/artifacts.zip org/graalvm/buildtools
popd
