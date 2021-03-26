package com.salesforce.bazel.maven.proxy.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.salesforce.bazel.maven.proxy.server.MavenProxyServerConfiguration.MavenRepository;

public class MavenProxyServerConfigurationTest {

	@TempDir
	static Path tempDirectory;

	private static Path sampleConfigFile;

	@BeforeAll
	protected static void setUp() throws Exception {
		URL sampleConfigFileUrl = MavenProxyServerConfigurationTest.class.getResource("/sample-proxy-config.yaml");
		assertNotNull(sampleConfigFileUrl, "sample-proxy-config.yaml is missing");

		Path tempConfigFile = tempDirectory.resolve("test-maven-settings.xml");
		try (InputStream in = sampleConfigFileUrl.openStream()) {
			FileUtils.copyInputStreamToFile(in, tempConfigFile.toFile());
		}

		sampleConfigFile = tempConfigFile;
	}

	@Test
	@DisplayName("Parses sample-proxy-config.yaml correctly")
	public void parsesSampleConfigCorrectly() throws Exception {
		MavenProxyServerConfiguration proxyServerConfiguration = MavenProxyServerConfiguration.loadFromFile(sampleConfigFile);
		assertNotNull(proxyServerConfiguration);

		assertNotNull(proxyServerConfiguration.mavenRepositories);
		assertEquals(2, proxyServerConfiguration.mavenRepositories.size());

		MavenRepository server1 = proxyServerConfiguration.mavenRepositories.get("server1");
		assertNotNull(server1);
		assertEquals("https://my.maven.server", server1.url);
		assertEquals("hey", server1.username);
		assertEquals("there", server1.password);

		MavenRepository server2 = proxyServerConfiguration.mavenRepositories.get("server2");
		assertNotNull(server2);
		assertEquals("https://my.2nd.maven.server", server2.url);
		assertNull(server2.username);
		assertNull(server2.password);
	}

}
