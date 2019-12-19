package junit5bazelhacks;

import java.util.List;
import java.util.ArrayList;

public class JUnit5Launcher {

    public static void main(String[] args) {
    	// workaround for https://github.com/bazelbuild/bazel/issues/8317
    	List<String> newargs = new ArrayList(List.of(args));
    	newargs.add("--reports-dir");
    	newargs.add(System.getenv("TEST_UNDECLARED_OUTPUTS_DIR"));
        org.junit.platform.console.ConsoleLauncher.main(newargs.toArray(new String[0]));
    }
}
