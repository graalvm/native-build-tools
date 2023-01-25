---
name: Bug report
about: Create a report to help us improve
title: ''
labels: bug
assignees: ''

---

**Before reporting**

- This repository should be used to report **issues on the Maven or Gradle plugins for GraalVM**.
- Please report issues which are specific to [the Spring Framework](https://spring.io/) or [the Micronaut framework](https://micronaut.io/) to their specific repositories.
- Do not report issues with building your specific application, e.g errors which happen at image build time like classes initialized at build time, or missing classes as run time: those are not related to the plugins but problems with configuration. You can refer to the [GraalVM native image documentation](https://www.graalvm.org/latest/reference-manual/native-image/) for available options and the [plugins documentation](https://graalvm.github.io/native-build-tools) for how to use them with the plugin.

**Describe the bug**
A clear and concise description of what the bug is.
**Make sure that you have read [the documentation](https://graalvm.github.io/native-build-tools) and that you are using the latest plugin version.**

**To Reproduce**

When possible, provide a link to a repository which reproduces the issue, with instructions on how to use.
The reproducer **must** make use of either the Maven or Gradle plugin.

Steps to reproduce the behavior:
```xml
<!-- project configuration -->
```
```bash
$ # command invocation 
```
Please use backticks to [properly format code](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-and-highlighting-code-blocks#syntax-highlighting).
If possible please attach a complete reproducer here (either as [a zip file](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/attaching-files) or as a link to public repository/branch).

**Expected behavior**
A clear and concise description of what you expected to happen.

**Logs**
Add logs to help explain your problem. 
Please use backticks to [properly format big logs](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-and-highlighting-code-blocks#fenced-code-blocks). Example:
````
```
<log content> 
```
````

**System Info (please complete the following information):**
 - OS: [e.g. `Windows`]
 - GraalVM Version [e.g. `22.0 CE`]
 - Java Version [e.g. `17`]
 - Plugin version [e.g. `native-gradle-plugin:0.9.10`]

**Additional context**
Add any other context about the problem here.
