package(default_visibility = ["//visibility:private"])

java_library(
    name = "server",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**"]),
    deps = [
    	"@maven//:info_picocli_picocli",
    	"@maven//:javax_servlet_javax_servlet_api",
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
		"@maven//:org_apache_commons_commons_compress",
		"@maven//:org_yaml_snakeyaml"
    ],
    visibility = ["//visibility:public"],
    runtime_deps = [
    	"@maven//:org_slf4j_slf4j_simple",
    ]
)
