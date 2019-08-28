package com.salesforce.bazel.maven.proxy.server;

import static java.lang.String.format;
import static java.nio.file.Paths.get;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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

import com.salesforce.bazel.maven.settings.MavenSettingsXmlParser;
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
		CommandLine.call(new MavenProxyServer(), args);
	}

	@Option(names = { "-p", "--port" }, description = "port to listen on (HTTP/2 and HTTP 1.1 with self-sign 'localhost' certificate)", defaultValue = "8499")
	private int port;

	@Option(names = { "--unsecure-port" }, description = "unsecure port to listen on (default is none, set to >0 to enable)")
	private int unsecurePort;

	@Option(names = { "-c", "--config-file" }, description = "proxy configuration file with additional repositories to proxy, i.e. path to proxy-config.yaml", paramLabel = "PROXY-CONFIG-YAML")
	private Path proxyConfigFile;

	@Option(names = { "-s", "--maven-settings" }, description = "path to Maven's settings.xml to read repositories and authentication informatiom from (default: ~/.m2/settings.xml)", paramLabel = "MAVEN-SETTINGS-XML")
	private Path mavenSettingsXml;

	@Option(names = { "--local-maven-repository" }, description = "path to Maven's local repositorty (default: ~/.m2/repository/)", paramLabel = "PATH")
	private Path mavenLocalRepositoryPath;

	@Override
	public Void call() throws Exception {
		// use system proxies
		System.setProperty("java.net.useSystemProxies", "true");

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
		SslContextFactory sslContextFactory = new SslContextFactory.Server();
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
		ServerConnector http2Connector = new ServerConnector(server, ssl, alpn, h2, new HttpConnectionFactory(httpsConfig));
		http2Connector.setPort(port);
		http2Connector.setHost("127.0.0.1");
		server.addConnector(http2Connector);

		// un-secure if enabled
		if (unsecurePort > 0) {
			LOG.warn("Configuring unsecure communication on port {}.", unsecurePort);
			ServerConnector connector = new ServerConnector(server);
			connector.setPort(unsecurePort);
			connector.setHost("127.0.0.1");
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
		new MavenSettingsXmlParser(mavenSettingsXml, (serverCredentials) -> credentials.put(serverCredentials.id, serverCredentials), (repository) -> {
			try {
				repositories.put(repository.id, new URL(repository.url));
			} catch (MalformedURLException e) {
				LOG.warn("Invalid URL in Maven Settings: {} ({}) - {}", repository.id, repository.url, e.getMessage());
			}
		}).parse();
	}

	private void registerServletForMavenRepository(ServletContextHandler handler, String id, URL url, ServerCredentials serverCredentials) {
		String prefix = format("/maven/%s", id);
		String proxyTo = url.toExternalForm();

		LOG.debug("Registering Maven Proxy Repository {} -> {}", prefix, proxyTo);

		ServletHolder proxyServlet = new ServletHolder(MavenProxyServlet.class);
		proxyServlet.setInitParameter(MavenProxyServlet.PROXY_TO, proxyTo);
		proxyServlet.setInitParameter("prefix", prefix);
		if (serverCredentials != null) {
			proxyServlet.setInitParameter(MavenProxyServlet.USERNAME, serverCredentials.username);
			proxyServlet.setInitParameter(MavenProxyServlet.PASSWORD, serverCredentials.password);
		}
		handler.addServlet(proxyServlet, format("%s/*", prefix));
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
			MavenProxyServerConfiguration proxyServerConfiguration = MavenProxyServerConfiguration.loadFromFile(proxyConfigFile);
			Optional.ofNullable(proxyServerConfiguration.mavenRepositories).ifPresent((mavenRepositories) -> {
				mavenRepositories.entrySet().forEach((entry) -> {
					try {
						String id = entry.getKey();
						URL targetUrl = new URL(entry.getValue().url);
						if (repositories.containsKey(entry.getKey())) {
							LOG.warn("Overriding repository '{}' found in Maven Settings with configuration found in config file.", id);
						}
						repositories.put(id, targetUrl);
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

		server.start();

		LOG.info(unsecurePort > 0 ? "Started Maven Proxy on ports {} and {}." : "Started Maven Proxy on port {}.", port, unsecurePort);
		return server;
	}

}
