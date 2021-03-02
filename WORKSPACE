workspace(name= "bazel_maven_proxy")

# To find the sha256 for an http_archive, run wget on the URL to download the
# file, and use sha256sum on the file to produce the sha256.

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# bazel-skylib 0.8.0 released 2019.03.20 (https://github.com/bazelbuild/bazel-skylib/releases/tag/0.8.0)
skylib_version = "0.8.0"
http_archive(
    name = "bazel_skylib",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib.{}.tar.gz".format (skylib_version, skylib_version),
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
)

# check minimum Bazel version
load("@bazel_skylib//lib:versions.bzl", "versions")
versions.check(minimum_bazel_version = "0.25.1")


# maven_install
RULES_JVM_EXTERNAL_TAG = "4.0"
RULES_JVM_EXTERNAL_SHA = "31701ad93dbfe544d597dbe62c9a1fdd76d81d8a9150c2bf1ecf928ecdf97169"
http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)
load("@rules_jvm_external//:defs.bzl", "maven_install")

# JUnit 5 Support
# (also needs to load tools/junit5/defs.bzl)
JUNIT5_API_VERSION = "5.7.1"
JUNIT5_PLATFORM_VERSION="1.7.1"

# Maven Dependencies
maven_install(
    name = "maven",
    artifacts = [
		"info.picocli:picocli:4.6.1",
		"commons-io:commons-io:2.8.0",
		"org.eclipse.jetty.http2:http2-client:11.0.1",
		"org.eclipse.jetty.http2:http2-http-client-transport:11.0.1",
		"org.eclipse.jetty.http2:http2-server:9.4.18.v20190429",
		"org.eclipse.jetty:jetty-alpn-java-server:11.0.1",
		"org.eclipse.jetty:jetty-alpn-server:11.0.1",
		"org.eclipse.jetty:jetty-proxy:11.0.1",
		"org.eclipse.jetty:jetty-server:11.0.1",
		"org.eclipse.jetty:jetty-servlet:11.0.1",
		"org.slf4j:slf4j-api:1.7.30",
		"org.slf4j:slf4j-simple:1.7.30",
		"org.yaml:snakeyaml:1.28",

		# JUnit 5
		"org.junit.jupiter:junit-jupiter-api:" + JUNIT5_API_VERSION,
        "org.junit.jupiter:junit-jupiter-engine:" + JUNIT5_API_VERSION,
        "org.junit.jupiter:junit-jupiter-params:" + JUNIT5_API_VERSION,
        "org.apiguardian:apiguardian-api:1.1.0",
        "org.opentest4j:opentest4j:1.2.0",
        "org.junit.platform:junit-platform-commons:"+ JUNIT5_PLATFORM_VERSION,
        "org.junit.platform:junit-platform-console:"+ JUNIT5_PLATFORM_VERSION,
        "org.junit.platform:junit-platform-engine:"+ JUNIT5_PLATFORM_VERSION,
        "org.junit.platform:junit-platform-launcher:"+ JUNIT5_PLATFORM_VERSION,
        "org.junit.platform:junit-platform-suite-api:"+ JUNIT5_PLATFORM_VERSION,
    ],
    repositories = [
	    "https://maven.google.com",
	    "https://repo1.maven.org/maven2",
    ],
    maven_install_json = "@bazel_maven_proxy//:maven_install.json",
)

# load pinned dependencies
load("@maven//:defs.bzl", "pinned_maven_install")
pinned_maven_install()
