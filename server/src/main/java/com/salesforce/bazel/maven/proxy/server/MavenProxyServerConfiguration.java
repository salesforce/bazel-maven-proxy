package com.salesforce.bazel.maven.proxy.server;

import static java.nio.file.Files.newInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class MavenProxyServerConfiguration {

	public static class MavenRepository {

		public String url;
		public String username;
		public String password;

	}

	public static MavenProxyServerConfiguration loadFromFile(Path configFile) throws IOException {
		Yaml yaml = new Yaml(new Constructor(MavenProxyServerConfiguration.class));
		try (InputStream in = newInputStream(configFile)) {
			return yaml.load(new BufferedInputStream(in));
		}
	}

	public Map<String, MavenRepository> mavenRepositories = new LinkedHashMap<>();

	public Map<String, MavenRepository> getMavenRepositories() {
		return mavenRepositories;
	}
}
