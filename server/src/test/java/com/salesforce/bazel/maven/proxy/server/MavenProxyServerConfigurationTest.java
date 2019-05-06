package com.salesforce.bazel.maven.proxy.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URL;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.salesforce.bazel.maven.proxy.server.MavenProxyServerConfiguration.MavenRepository;

public class MavenProxyServerConfigurationTest {

	private static Path sampleConfigFile;

	@BeforeAll
	protected static void setUp() throws Exception {
		URL sampleConfigFileUrl = MavenProxyServerConfigurationTest.class.getResource("/sample-proxy-config.yaml");
		assertNotNull(sampleConfigFileUrl, "sample-proxy-config.yaml is missing");
		sampleConfigFile = Path.of(sampleConfigFileUrl.toURI());
	}

	@Test
	@DisplayName("Parses sample-proxy-config.yaml correctly")
	public void parsesSampleConfigCorrectly() throws Exception {
		MavenProxyServerConfiguration proxyServerConfiguration = MavenProxyServerConfiguration.loadFromFile(sampleConfigFile);
		assertNotNull(proxyServerConfiguration);

		assertNotNull(proxyServerConfiguration.mavenRepositories);
		assertEquals(1, proxyServerConfiguration.mavenRepositories.size());

		MavenRepository server1 = proxyServerConfiguration.mavenRepositories.get("server1");
		assertNotNull(server1);
		assertEquals("https://my.maven.server", server1.url);
	}

}
