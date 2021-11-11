package com.salesforce.bazel.maven.proxy.server;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Paths.get;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.xml.stream.XMLStreamException;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.maven.proxy.server.MavenProxyServerConfiguration.MavenRepository;
import com.salesforce.bazel.maven.settings.MavenSettingsXmlParser;
import com.salesforce.bazel.maven.settings.MavenSettingsXmlParser.Mirror;
import com.salesforce.bazel.maven.settings.MavenSettingsXmlParser.ServerCredentials;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main class for starting/stopping the server
 */
@Command(name = "bazel-maven-proxy", description = "Starts the Bazel Maven Proxy", mixinStandardHelpOptions = true, version = "n/a")
public class MavenProxyServer implements Callable<Void> {

	private static final Logger LOG = LoggerFactory.getLogger(MavenProxyServer.class);

	public static void main(String[] args) {
		System.exit(new CommandLine(new MavenProxyServer()).execute(args));
	}

	@Option(names = { "-p", "--port" }, description = "port to listen on (HTTP/2 and HTTP 1.1 with self-sign 'localhost' certificate)", defaultValue = "8499")
	private int port;

	@Option(names = { "--unsecure-port" }, description = "non-secure (plain HTTP) port to listen on (default is none, set to >0 to enable)")
	private int unsecurePort;

	@Option(names = { "--host" }, description = "host name to listen on (default is 127.0.0.1, i.e. only local connections allowed; use 0.0.0.0 to listen on all interfaces)", defaultValue = "127.0.0.1")
	private String host;

	@Option(names = { "-c", "--config-file" }, description = "proxy configuration file with (additional) repositories to proxy, i.e. path to proxy-config.yaml", paramLabel = "PROXY-CONFIG-YAML")
	private Path proxyConfigFile;

	@Option(names = { "-s", "--maven-settings" }, description = "path to Maven's settings.xml to read repositories and authentication information (default is ~/.m2/settings.xml)", paramLabel = "MAVEN-SETTINGS-XML")
	private Path mavenSettingsXml;

	@Option(names = { "--local-maven-repository" }, description = "path to Maven's local repository (default: ~/.m2/repository/)", paramLabel = "PATH")
	private Path mavenLocalRepositoryPath;

	@Override
	public Void call() throws Exception {
		// configure and start Jetty
		Server server = startJetty();

		// wait for process to die
		server.join();

		return null;
	}

	Server createJettyServer() {
		Server server = new Server();

		// HTTP configuration
		HttpConfiguration httpConfig = new HttpConfiguration();
		httpConfig.setSendServerVersion(false);
		httpConfig.setSendXPoweredBy(false);
		httpConfig.setSecureScheme("https");
		httpConfig.setSecurePort(port);

		// SSL context factory (HTTPS and HTTP/2)
		SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
		sslContextFactory.setKeyStorePath(MavenProxyServer.class.getResource("localhost.pkcs12").toExternalForm());
		sslContextFactory.setKeyStorePassword("localhost");
		sslContextFactory.setKeyStoreType("pkcs12");
		sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);

		// HTTPS configuration
		HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
		httpsConfig.addCustomizer(new SecureRequestCustomizer());

		// HTTP/2 connection factory
		HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(httpsConfig);
		ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
		alpn.setDefaultProtocol("h2");

		// SSL connection factory
		SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());

		// HTTP/2 Connector
		LOG.info("Configuring secure communication on {}:{}.", host, port);
		ServerConnector http2Connector = new ServerConnector(server, ssl, alpn, h2, new HttpConnectionFactory(httpsConfig));
		http2Connector.setPort(port);
		http2Connector.setHost(host);
		server.addConnector(http2Connector);

		// un-secure if enabled
		if (unsecurePort > 0) {
			LOG.warn("Configuring unsecure communication on port {}:{}.", host, unsecurePort);
			ServerConnector connector = new ServerConnector(server);
			connector.setPort(unsecurePort);
			connector.setHost(host);
			server.addConnector(connector);
		}

		return server;
	}

	private MavenRepositoryCache createLocalMavenRepositoryCache() {
		// initialize Maven settings
		if (mavenLocalRepositoryPath == null) {
			mavenLocalRepositoryPath = get(System.getProperty("user.home")).resolve(".m2/repository");
		}

		return new MavenRepositoryCache(mavenLocalRepositoryPath);
	}

	private void readMavenSettings(Map<String, ServerCredentials> credentials, Map<String, URL> repositories) throws XMLStreamException, IOException {
		// initialize Maven settings
		if (mavenSettingsXml == null) {
			mavenSettingsXml = get(System.getProperty("user.home")).resolve(".m2/settings.xml");
		}

		// parse
		if (isRegularFile(mavenSettingsXml)) {
			LOG.info("Reading Maven settings found at '{}'.", mavenSettingsXml);
			List<Mirror> mirrors = new ArrayList<>();
			new MavenSettingsXmlParser(mavenSettingsXml, (serverCredentials) -> credentials.put(serverCredentials.id, serverCredentials), (repository) -> {
				try {
					repositories.put(repository.id, new URL(repository.url));
				} catch (MalformedURLException e) {
					LOG.warn("Invalid URL in Maven Settings: {} ({}) - {}", repository.id, repository.url, e.getMessage());
				}
			}, mirrors::add).parse();

			// always inject 'central'
			if (!repositories.containsKey("central")) {
				repositories.put("central", new URL("https://repo.maven.apache.org/maven2"));
			}

			// basic mirror processing
			if (!mirrors.isEmpty()) {
				// replace repository urls with mirror urls when needed
				for (Entry<String, URL> repository : repositories.entrySet()) {
					String repositoryId = repository.getKey();

					// check for a mirror
					for (Mirror mirror : mirrors) {
						// see
						// https://maven.apache.org/guides/mini/guide-mirror-settings.html
						// for Advanced Mirror Specification
						Set<String> mirroredRepositoryIds = new LinkedHashSet<>(Arrays.asList(mirror.mirrorOf.split(",")));
						if (mirroredRepositoryIds.contains(repositoryId) // direct
																			// mirror
								|| (mirroredRepositoryIds.contains("*") && !mirroredRepositoryIds.contains("!" + repositoryId)) // indirect
																																// mirror
																																// (via
																																// *
																																// but
																																// no
																																// exclusion)
						) {
							try {
								// update repository URL
								repository.setValue(new URL(mirror.url));
								// log result (when URL was parsed without
								// error)
								LOG.info("Found mirror '{}' in Maven settings for repository '{}'", mirror.id, repositoryId);
								// ensure mirror credentials are mapped to
								// repository because of updated url
								if (credentials.containsKey(mirror.id)) {
									LOG.debug("Overriding repository '{}' credentials with credentials for mirror '{}'", repositoryId, mirror.id);
									credentials.put(repositoryId, credentials.get(mirror.id));
								}
							} catch (MalformedURLException e) {
								LOG.warn("Invalid mirror URL in Maven Settings: {} ({}) - {}", mirror.id, mirror.url, e.getMessage());
							}
						}
					}
				}

			}
		} else {
			LOG.info("No Maven settings found at '{}'. Proxy needs a config file.", mavenSettingsXml);
		}
	}

	private void registerServletForMavenRepository(ServletContextHandler handler, String id, URL url, ServerCredentials serverCredentials) {
		String prefix = format("/maven/%s", id);
		String proxyTo = url.toExternalForm();

		LOG.debug("Registering Maven Proxy Repository {} -> {} (using credentials {})", prefix, proxyTo, serverCredentials != null ? serverCredentials.id : "none");

		ServletHolder proxyServlet = new ServletHolder(MavenProxyServlet.class);
		proxyServlet.setInitParameter(MavenProxyServlet.PROXY_TO, proxyTo);
		proxyServlet.setInitParameter("prefix", prefix);
		if (serverCredentials != null) {
			proxyServlet.setInitParameter(MavenProxyServlet.USERNAME, serverCredentials.username);
			proxyServlet.setInitParameter(MavenProxyServlet.PASSWORD, serverCredentials.password);
		}
		handler.addServlet(proxyServlet, format("%s/*", prefix));
	}

	private void registerServletForMavenRepositoryList(ServletContextHandler handler, Map<String, URL> repositories) {
		handler.setAttribute(MavenRepositoryListServlet.REPOSITORIES_MAP, repositories);
		handler.addServlet(new ServletHolder(MavenRepositoryListServlet.class), "/maven");
	}

	private Server startJetty() throws Exception {
		LOG.debug("Starting embedded Jetty server...");
		Server server = createJettyServer();

		ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
		handler.setContextPath("/");
		server.setHandler(handler);

		// setup common Maven mappings
		handler.getMimeTypes().addMimeMapping("pom", "application/xml");
		handler.getMimeTypes().addMimeMapping("jar", "application/java-archive");
		handler.getMimeTypes().addMimeMapping("sha1", "text/plain");

		Map<String, ServerCredentials> credentials = new HashMap<>();
		Map<String, URL> repositories = new LinkedHashMap<>();
		readMavenSettings(credentials, repositories);

		if (proxyConfigFile != null) {
			LOG.info("Loading configuration from '{}'.", proxyConfigFile);
			MavenProxyServerConfiguration proxyServerConfiguration = MavenProxyServerConfiguration.loadFromFile(proxyConfigFile);
			Optional.ofNullable(proxyServerConfiguration.mavenRepositories).ifPresent((mavenRepositories) -> {
				mavenRepositories.entrySet().forEach((entry) -> {
					try {
						String id = entry.getKey();
						MavenRepository repository = entry.getValue();
						URL targetUrl = new URL(repository.url);
						if (repositories.containsKey(id)) {
							LOG.warn("Overriding repository '{}' found in Maven Settings with configuration found in config file.", id);
						}
						repositories.put(id, targetUrl);

						if (((repository.username != null) && !repository.username.isBlank()) && ((repository.password != null) && !repository.password.isBlank())) {
							ServerCredentials serverCredentials = new ServerCredentials();
							serverCredentials.id = id;
							serverCredentials.username = repository.username;
							serverCredentials.password = repository.password;
							if (credentials.containsKey(id)) {
								LOG.warn("Overriding credentials for repository '{}' found in Maven Settings with configuration found in config file.", id);
							}
							credentials.put(id, serverCredentials);
						}
					} catch (Exception e) {
						throw new IllegalArgumentException(format("Invalid repository entry in proxy configuration: %s - %s", entry.getKey(), e.getMessage()), e);
					}
				});
			});
		}

		MavenRepositoryCache cache = createLocalMavenRepositoryCache();
		LOG.debug("Using local Maven Repository: {}", cache.getLocalRepositoryPath());
		handler.setAttribute(MavenRepositoryCache.class.getName(), cache);

		repositories.forEach((id, url) -> {
			registerServletForMavenRepository(handler, id, url, credentials.get(id));
		});

		registerServletForMavenRepositoryList(handler, repositories);

		server.start();

		LOG.info(unsecurePort > 0 ? "Started Maven Proxy on ports {} and {}." : "Started Maven Proxy on port {}.", port, unsecurePort);
		return server;
	}

}
