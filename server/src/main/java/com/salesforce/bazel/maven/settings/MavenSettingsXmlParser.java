package com.salesforce.bazel.maven.settings;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.newInputStream;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Maven's settings.xml
 */
public class MavenSettingsXmlParser {

	interface ChildElementHandler {
		void onChildElement(String elementName) throws XMLStreamException;
	}

	public static class Repository {
		public String id, url;
	}

	public interface RepositoryHandler {
		void onRepository(Repository repository);
	}

	public static class ServerCredentials {
		public String id, username, password;
	}

	public interface ServerCredentialsHandler {
		void onServerCredentials(ServerCredentials serverCredentials);
	}

	private static final Logger LOG = LoggerFactory.getLogger(MavenSettingsXmlParser.class);

	static String eventTypeName(int eventType) {
		switch (eventType) {
		case START_ELEMENT:
			return "start tag";

		case END_ELEMENT:
			return "end element";

		default:
			return String.valueOf(eventType);
		}
	}

	private final Path settingsXmlFile;
	private int readElementChildrenLevel;
	private final ServerCredentialsHandler credentialsHandler;
	private final RepositoryHandler repositoryHandler;

	public MavenSettingsXmlParser(Path settingsXmlFile, ServerCredentialsHandler credentialsHandler, RepositoryHandler repositoryHandler) {
		this.settingsXmlFile = settingsXmlFile;
		this.credentialsHandler = credentialsHandler;
		this.repositoryHandler = repositoryHandler;
	}

	public void parse() throws XMLStreamException, IOException {
		if (!isRegularFile(settingsXmlFile))
			throw new IllegalArgumentException(format("'%s' not found. Please specify correct path to Maven's settings.xml!", settingsXmlFile));

		try (InputStream in = newInputStream(settingsXmlFile)) {
			readSettingsXml(new BufferedInputStream(in));
		}
	}

	private void readElementChildren(XMLStreamReader streamReader, ChildElementHandler nestedElementHandler) throws XMLStreamException {
		readElementChildrenLevel++;
		try {

			int level = 0;
			while (streamReader.hasNext()) {

				switch (streamReader.getEventType()) {
				case START_ELEMENT:
					if (LOG.isTraceEnabled()) {
						readElementChildrenTrace("<{} ({})>", streamReader.getLocalName(), level);
					}
					if (level > 0) {
						if (nestedElementHandler != null) {
							streamReader.getLocation();
							String nestedElementName = streamReader.getLocalName();
							nestedElementHandler.onChildElement(nestedElementName);
							readElementChildrenTrace("AFTER nestedElementHandler {}: {}", level, eventTypeName(streamReader.getEventType()));
							// a nested element handler is allowed to read the
							// nested element completely,
							if ((streamReader.getEventType() == END_ELEMENT) && streamReader.getLocalName().equals(nestedElementName)) {
								// in this case we don't increase the level
								streamReader.next(); // continue with next event
								break;
							}
						}
					}
					level++;
					streamReader.next(); // continue with next event
					break;

				case END_ELEMENT:
					level--;
					if (LOG.isTraceEnabled()) {
						readElementChildrenTrace("</{} ({})>", streamReader.getLocalName(), level);
					}
					if (level > 0) {
						// all good, continue reading
						streamReader.next();
						break;
					} else if (level < 0)
						throw new XMLStreamException("Processed more END_ELEMENT than START_ELEMENT.", streamReader.getLocation());
					readElementChildrenTrace("DONE {}: {}", level, streamReader.getLocalName());
					return; // done (and don't advance to next event)

				default:
					// ignore event
					streamReader.next();
					break;
				}
			}
		} finally {
			readElementChildrenLevel--;
		}
	}

	private void readElementChildrenTrace(String format, Object... arguments) {
		if (!LOG.isTraceEnabled())
			return;

		String prefix = "";
		int width = (readElementChildrenLevel * 2) - 2;
		for (int i = 0; i < width; i++) {
			prefix += "  ";
		}

		LOG.trace(prefix + format, arguments);
	}

	private void readRepository(XMLStreamReader streamReader) throws XMLStreamException {
		Repository repository = new Repository();

		readElementChildren(streamReader, (childName) -> {
			if (childName.equals("id")) {
				repository.id = streamReader.getElementText();
			} else if (childName.equals("url")) {
				repository.url = streamReader.getElementText();
			}
		});

		if ((repository.id == null) || (repository.url == null)) {
			LOG.trace("Ignoring incomplete repository: {}:{}", repository.id, repository.url);
			return;
		}

		LOG.trace("Found repository: {}:{}", repository.id, repository.url);
		repositoryHandler.onRepository(repository);
	}

	private void readServer(XMLStreamReader streamReader) throws XMLStreamException {
		ServerCredentials serverCredentials = new ServerCredentials();

		readElementChildren(streamReader, (childName) -> {
			if (childName.equals("id")) {
				serverCredentials.id = streamReader.getElementText();
			} else if (childName.equals("username")) {
				serverCredentials.username = streamReader.getElementText();
			} else if (childName.equals("password")) {
				serverCredentials.password = streamReader.getElementText();
			}
		});

		if ((serverCredentials.id == null) || (serverCredentials.username == null) || (serverCredentials.password == null)) {
			LOG.trace("Ignoring incomplete server credentials: {}:{}:{}", serverCredentials.id, serverCredentials.username, serverCredentials.password != null ? "******" : null);
			return;
		}

		LOG.trace("Found server credentials: {}:{}:{}", serverCredentials.id, serverCredentials.username, serverCredentials.password != null ? "******" : null);
		credentialsHandler.onServerCredentials(serverCredentials);
	}

	private void readSettings(XMLStreamReader streamReader) throws XMLStreamException {
		readElementChildren(streamReader, (childName) -> {
			if (childName.equals("server")) {
				readServer(streamReader);
			} else if (childName.equals("repository")) {
				readRepository(streamReader);
			}
		});
	}

	private void readSettingsXml(BufferedInputStream in) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLStreamReader streamReader = factory.createXMLStreamReader(in);

		while (streamReader.hasNext()) {
			streamReader.next();

			if (streamReader.getEventType() == XMLStreamConstants.START_ELEMENT) {
				String elementName = streamReader.getLocalName();
				if ("settings".equals(elementName)) {
					readSettings(streamReader);

					// done parsing
					LOG.trace("Done reading artifacts.");
					return;
				}
			}
		}

		LOG.warn("Abnormal finish. Incomplete or unsupported artifacts.xml");
	}

}
