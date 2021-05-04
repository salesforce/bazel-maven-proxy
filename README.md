# Bazel Maven Proxy
A local (read-only) proxy for Bazel to access Maven resources behind a secure repository or from the local Maven repo

## Features
* Provides password-less access to Maven repositories requiring authentication via `https://127.0.0.1:8499/maven/<repository-id>/...` (the `repository-id` is used from `~/.m2/settings.xml`)
* Reads Maven authentication information from `~/.m2/settings.xml`
* Delivers artifacts from local Maven repository (`~/.m2/repository`) when available
* Secure by default
    * Only listens locally on `127.0.0.1` (no external access possible)
    * Encrypted access with self-signed certificate via `https://localhost:8499/`
* Unsecure access must be explicitly enable via command line parameter
* Supports HTTP/2 access and can talk HTTP/2 or HTTP/1.1 to back-end Maven repositories

## Getting Started

* Build: `bazel build //:maven_proxy`
* Run: `bazel run //:maven_proxy -- --help`  (on Linux/macOS)

### Different JDK

In case you need to use a different JDK you can create a `.bazelrc-local` file.
Please have a look at `.bazelrc` for further instructions.

## Command Line Arguments

```
Usage: bazel-maven-proxy [--local-maven-repository=PATH]
                         [--unsecure-port=<unsecurePort>]
                         [-c=PROXY-CONFIG-YAML] [-p=<port>]
                         [-s=MAVEN-SETTINGS-XML]
Starts the Bazel Maven Proxy
  -c, --config-file=PROXY-CONFIG-YAML
                      proxy configuration file with (additional) repositories to
                        proxy, i.e. path to proxy-config.yaml
  -h, --help          Show this help message and exit.
      --host=<host>   host name to listen on (default is 127.0.0.1, i.e. only
                        local connections allowed; use 0.0.0.0 to listen on all
                        interfaces)
      --local-maven-repository=PATH
                      path to Maven's local repository (default: ~/.
                        m2/repository/)
  -p, --port=<port>   port to listen on (HTTP/2 and HTTP 1.1 with self-sign
                        'localhost' certificate)
  -s, --maven-settings=MAVEN-SETTINGS-XML
                      path to Maven's settings.xml to read repositories and
                        authentication information (default is ~/.
                        m2/settings.xml)
      --unsecure-port=<unsecurePort>
                      non-secure (plain HTTP) port to listen on (default is none, set to >0 to
                        enable)
  -V, --version       Print version information and exit.
```

## How to Use

Please ensure that your `~/.m2/settings.xml` has an entry for your repository like this:
```
  ...
  <servers>
    <server>
      <id>mynexus</id>
      <username>...</username>
      <password>...</password>
    </server>
    <server>
      <id>central</id>
      <username>...</username>
      <password>...</password>
    </server>
  </servers>
  ...

  <profiles>
    <profile>
      <id>mynexus</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://my-central-mirror.internal.network.com/..</url>
        </repository>
        <repository>
          <id>mynexus</id>
          <url>https://my-nexus-repo.internal.network.com/..</url>
        </repository>
      </repositories>
      ...
    </profile>
  </profiles>
```

Then in Bazel (or anywhere else) you can refer to these as `http(s)://localhost:<port>/maven/mynexus/..` and `http(s)://localhost:<port>/maven/central/..`.

Alternatively to (or in addition to) `~/.m2/settings.xml` one can also provide a YAML configuration file.
See an example [here](server/src/test/resources/sample-proxy-config.yaml) for syntax.