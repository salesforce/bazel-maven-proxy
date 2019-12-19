# inspired from:
# - https://github.com/bazelbuild/bazel/issues/6681#issuecomment-554978379
# - https://github.com/junit-team/junit5-samples/blob/master/junit5-jupiter-starter-bazel/junit5.bzl

def java_junit5_test(name, srcs, test_packages = [], deps = [], runtime_deps = [], **kwargs):
    FILTER_KWARGS = [
        "main_class",
        "use_testrunner",
        "args",
    ]

    for arg in FILTER_KWARGS:
        if arg in kwargs.keys():
            kwargs.pop(arg)

    if len(test_packages) == 0:
        fail("must specify non-empty 'test_packages'")

    junit_console_args = []
    for test_package in test_packages:
        junit_console_args += ["--select-package", test_package]

    junit_console_args  += ["--disable-ansi-colors", "--disable-banner", "--fail-if-no-tests"]

    native.java_test(
        name = name,
        srcs = srcs,
        use_testrunner = False,
        main_class = "junit5bazelhacks.JUnit5Launcher",
        args = junit_console_args,
        deps = deps + [
            "@maven//:org_junit_jupiter_junit_jupiter_api",
            "@maven//:org_junit_jupiter_junit_jupiter_engine",
            "@maven//:org_junit_jupiter_junit_jupiter_params",
            "@maven//:org_junit_platform_junit_platform_suite_api",
            "@maven//:org_apiguardian_apiguardian_api",
            "@maven//:org_opentest4j_opentest4j",
            "//tools/junit5:junit5_launcher",
        ],
        runtime_deps = runtime_deps + [
            "@maven//:org_junit_platform_junit_platform_commons",
            "@maven//:org_junit_platform_junit_platform_console",
            "@maven//:org_junit_platform_junit_platform_engine",
            "@maven//:org_junit_platform_junit_platform_launcher",
            "@maven//:org_junit_platform_junit_platform_suite_api",
        ],
        **kwargs
    )

