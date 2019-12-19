package com.salesforce.bazel.maven.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.salesforce.bazel.maven.settings.MavenSettingsXmlParser.Repository;
import com.salesforce.bazel.maven.settings.MavenSettingsXmlParser.ServerCredentials;

public class MavenSettingsXmlParserTest {

	@TempDir
	static Path tempDirectory;

	private static Path sampleSettingsFile;

	@BeforeAll
	protected static void setUp() throws Exception {
		URL sampleSettingsFileUrl = MavenSettingsXmlParserTest.class.getResource("/sample-maven-settings.xml");
		assertNotNull(sampleSettingsFileUrl, "sample settings.xml is missing");

		Path tempSettingsXml = tempDirectory.resolve("test-maven-settings.xml");
		try (InputStream in = sampleSettingsFileUrl.openStream()) {
			FileUtils.copyInputStreamToFile(in, tempSettingsXml.toFile());
		}

		sampleSettingsFile = tempSettingsXml;
	}

	@Test
	@DisplayName("Parses sample settings.xml correctly")
	public void parsesSampleSettingsCorrectly() throws Exception {
		List<ServerCredentials> credentials = new ArrayList<>();
		List<Repository> repositories = new ArrayList<>();
		new MavenSettingsXmlParser(sampleSettingsFile, credentials::add, repositories::add).parse();

		Optional<ServerCredentials> server1 = credentials.stream().filter((s) -> s.id.equals("server1")).findFirst();
		assertTrue(server1.isPresent(), "server1 not found");

		assertEquals("server1", server1.get().id);
		assertEquals("abc", server1.get().username);
		assertEquals("def", server1.get().password);

		Optional<ServerCredentials> central = credentials.stream().filter((s) -> s.id.equals("central")).findFirst();
		assertTrue(central.isPresent(), "central not found");

		assertEquals("central", central.get().id);
		assertEquals("123", central.get().username);
		assertEquals("456", central.get().password);

		assertEquals(2, credentials.size(), "two credentials should be in list");
	}

}
