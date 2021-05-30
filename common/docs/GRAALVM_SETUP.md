# Setting up GraalVM with native-image support
![GraalVM](https://www.graalvm.org/resources/img/logo-colored.svg)

Working GraalVM distribution with `native-image` installable and `GRAALVM_HOME` and/or `JAVA_HOME` environment variables set, is prequisite for successful *native-image* building.

Following are the steps needed to obtain and setup GraalVM environment.

> Alternatively, we have provided a [script](../scripts/downloadGraalVM.sh) for downloading and setting up latest nightly in CI environment.

Notice that this is just a quick overview, and that user should consult [Getting Started section](https://www.graalvm.org/docs/getting-started/) in official documentation before proceeding.

## 1. Obtaining distribution
GraalVM distributions can be obtained from [official website](https://www.graalvm.org/downloads/). Dev builds might be available at `releases` section of [official GraalVM Github page projects](https://github.com/graalvm/?q=graalvm-ce).

## 2. Setting up environment variables
After obtaining GraalVM distribution environment variable `GRAALVM_HOME` should be set to point to it. This can be achieved using:

Linux:
```bash
export GRAALVM_HOME=/home/${current_user}/path/to/graalvm
```
macOS:
```bash
export GRAALVM_HOME=/Users/${current_user}/path/to/graalvm/Contents/Home
```
Windows:
```batch
setx /M GRAALVM_HOME "C:\path\to\graalvm"
```

> Preferably user would also set `JAVA_HOME` variable in the same manner (by replacing `GRAALVM_HOME` with `JAVA_HOME` in previous commands).

## 3. `native-image` tool instalation

Linux / macOS:
```bash
$GRAALVM_HOME/bin/gu install native-image
```
Windows:
```batch
%GRAALVM_HOME%/bin/gu install native-image
```




