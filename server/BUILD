package(default_visibility = ["//visibility:private"])

java_library(
    name = "server",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**"]),
    deps = [
    	"@maven//:info_picocli_picocli",
    	"@maven//:org_slf4j_slf4j_api",
    	"@maven//:org_eclipse_jetty_http2_http2_client",
    	"@maven//:org_eclipse_jetty_http2_http2_common",
    	"@maven//:org_eclipse_jetty_http2_http2_http_client_transport",
    	"@maven//:org_eclipse_jetty_http2_http2_server",
		"@maven//:org_eclipse_jetty_jetty_alpn_java_server",
		"@maven//:org_eclipse_jetty_jetty_alpn_server",
		"@maven//:org_eclipse_jetty_jetty_http",
		"@maven//:org_eclipse_jetty_jetty_proxy",
		"@maven//:org_eclipse_jetty_jetty_server",
		"@maven//:org_eclipse_jetty_jetty_servlet",
		"@maven//:org_eclipse_jetty_jetty_util",
    	"@maven//:org_eclipse_jetty_toolchain_jetty_jakarta_servlet_api",
		"@maven//:org_yaml_snakeyaml"
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
    	"@maven//:org_slf4j_slf4j_simple",
    ]
)

load("//tools/junit5:defs.bzl", "java_junit5_test")
java_junit5_test(
    name = "server_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    test_packages = [
        "com.salesforce.bazel.maven.proxy.server",
        "com.salesforce.bazel.maven.settings",
    ],
    resources = glob(["src/test/resources/**"]),
    deps = [
    	":server",
		"@maven//:commons_io_commons_io",
    ],
    visibility = ["//visibility:__subpackages__"],
    runtime_deps = [
    	"@maven//:org_slf4j_slf4j_simple",
    ]
)
