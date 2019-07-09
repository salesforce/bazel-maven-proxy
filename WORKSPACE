workspace(name= "bazel_maven_proxy")

# To find the sha256 for an http_archive, run wget on the URL to download the
# file, and use sha256sum on the file to produce the sha256.

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_jar")
load("@bazel_tools//tools/build_defs/repo:git.bzl",  "git_repository", "new_git_repository")

git_repository(
    name = "bazel_skylib",
    remote = "https://github.com/bazelbuild/bazel-skylib.git",
    tag = "0.6.0",
)

# check minimum Bazel version
load("@bazel_skylib//lib:versions.bzl", "versions")
versions.check(minimum_bazel_version = "0.25.1")


# maven_install
RULES_JVM_EXTERNAL_TAG = "2.1"
RULES_JVM_EXTERNAL_SHA = "515ee5265387b88e4547b34a57393d2bcb1101314bcc5360ec7a482792556f42"
http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)
load("@rules_jvm_external//:defs.bzl", "maven_install")

# Maven Dependencies
maven_install(
    name = "maven",
    artifacts = [
		"info.picocli:picocli:3.8.2",
		"org.apache.commons:commons-compress:1.18",
		"org.eclipse.jetty.http2:http2-client:9.4.18.v20190429",
		"org.eclipse.jetty.http2:http2-http-client-transport:9.4.18.v20190429",
		"org.eclipse.jetty.http2:http2-server:9.4.18.v20190429",
		"org.eclipse.jetty:jetty-alpn-java-server:9.4.18.v20190429",
		"org.eclipse.jetty:jetty-alpn-server:9.4.18.v20190429",
		"org.eclipse.jetty:jetty-proxy:9.4.18.v20190429",
		"org.eclipse.jetty:jetty-server:9.4.18.v20190429",
		"org.eclipse.jetty:jetty-servlet:9.4.18.v20190429",
		"org.junit.jupiter:junit-jupiter-api:5.3.2",
		"org.junit.jupiter:junit-jupiter-engine:5.3.2",
		"org.slf4j:slf4j-api:1.7.25",
		"org.slf4j:slf4j-simple:1.7.25",
		"org.yaml:snakeyaml:1.24",
    ],
    repositories = [
	    "https://jcenter.bintray.com/",
	    "https://maven.google.com",
	    "https://repo1.maven.org/maven2",
    ],
)
