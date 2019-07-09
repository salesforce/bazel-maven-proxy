package(default_visibility = ["//visibility:private"])

java_binary(
    name = "maven_proxy",
    main_class = "com.salesforce.bazel.maven.proxy.server.MavenProxyServer",
    runtime_deps = [
    	"//server"
    ],
    visibility = ["//visibility:public"],
)