# Bazel Maven Proxy
A local (read-only) proxy for Bazel to access Maven resources behind a secure repository or from the local Maven repo

## Features
* Provides password-less access to Maven repositories requiring authentication via `https://127.0.0.1:8499/maven/...`
* Reads Maven authentication information from `~/.m2/settings.xml`
* Delivers artifacts from local Maven repository (`~/.m2/repository`) when available
* Secure by default
    * Only listens locally on `127.0.0.1` (no external access possible)
    * Encrypted access with self-signed certificate via `https://localhost:8499/`
* Unsecure access must be explicetly enable via command line parameter
* Supports HTTP/2 access and can talk HTTP/2 or HTTP/1.1 to back-end Maven repositories

## Getting Started

* Java 11 is required: `export JAVA_HOME=<path/to/JDK11>` with path to JDK 11
* Build: `mvn clean package`
* Run: `server/target/bazel-maven-proxy-server-1.0.0-SNAPSHOT-standalone.jar --help` (on Linux/macOS)

## Command Line Arguments

```
Usage: bazel-maven-proxy [--local-maven-repository=PATH]
                         [--unsecure-port=<unsecurePort>]
                         [-c=PROXY-CONFIG-YAML] [-p=<port>]
                         [-s=MAVEN-SETTINGS-XML]
Starts the Bazel Maven Proxy
      --local-maven-repository=PATH
                      path to Maven's local repositorty (default: ~/.m2/repository/)
      --unsecure-port=<unsecurePort>
                      unsecure port to listen on (default is none, set to >0 to
                        enable)
  -c, --config-file=PROXY-CONFIG-YAML
                      proxy configuration file with additional repositories to
                        proxy, i.e. path to proxy-config.yaml
  -p, --port=<port>   port to listen on (HTTP/2 and HTTP 1.1 with self-sign
                        'localhost' certificate)
  -s, --maven-settings=MAVEN-SETTINGS-XML
                      path to Maven's settings.xml to read repositories and
                        authentication informatiom from (default: ~/.m2/settings.xml)
```
