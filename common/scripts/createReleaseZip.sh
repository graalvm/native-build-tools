#!/bin/bash -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
pushd $SCRIPT_DIR/../..

rm -rdf ~/.m2/repository/org/graalvm/buildtools

if [ ! -f version.properties.bak ]; then
    cp version.properties version.properties.bak
fi

sed -i 's/-SNAPSHOT//' version.properties
common/scripts/updateVersions.sh
common/scripts/buildAll.sh

if [ "$1" != "--skiptests" ]; then
    common/scripts/testAll.sh
fi

mv version.properties.bak version.properties
common/scripts/updateVersions.sh

rm -rdf build
mkdir -p build
cd build
mkdir -p org/graalvm/
cp -r ~/.m2/repository/org/graalvm/buildtools org/graalvm/buildtools
find . -name '*.xml*' -delete
find . -name '*.repositories' -delete
find . -name '*.properties' -delete

zip -r artifacts.zip org/graalvm/buildtools
rm -rdf org
popd
