#!/bin/bash -e

urldecode() {
  : "${*//+/ }"
  echo -e "${_//%/\\x}"
}

LATEST_GRAAL_URL=$(curl -s "https://api.github.com/repos/graalvm/graalvm-ce-dev-builds/releases" | \
  grep -Po "['\"]browser_download_url['\"]\s*:\s*['\"]\K(.*)(?=['\"])" | \
  grep -m1 "graalvm-ce-java11-linux-amd64-.*-dev.tar.gz")

LATEST_GRAAL_FILENAME=$(urldecode "$(basename "$LATEST_GRAAL_URL")")

if [ ! -f "$LATEST_GRAAL_FILENAME" ]; then
  echo "Downloading GraalVM Nightly..."
  wget --continue --quiet "$LATEST_GRAAL_URL"
  echo "Done."
fi

LATEST_GRAAL_DIR=$(tar -tzf "$LATEST_GRAAL_FILENAME" | head -1 | cut -f1 -d"/")

if [ ! -d "$LATEST_GRAAL_DIR" ]; then
  tar -xzf "$LATEST_GRAAL_FILENAME" -C "."
fi

mv "$LATEST_GRAAL_DIR" graalvm

export GRAALVM_HOME="$PWD/graalvm"
export JAVA_HOME="$GRAALVM_HOME"
export PATH="$GRAALVM_HOME/bin:$PATH"

gu install native-image

echo "JAVA_HOME=$JAVA_HOME"
