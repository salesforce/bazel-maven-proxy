//
//  Based on Jetty's AbstractProxyServlet, adapted to use OpenJDK HttpClient
//
//  AbstractProxyServlet.java:
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//
package com.salesforce.bazel.maven.proxy.server;

import static java.lang.String.format;
import static java.nio.file.Paths.get;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.UnavailableException;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.HttpOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenProxyServlet extends HttpServlet {

	/**
	 * Streams from and {@link InputStream} to {@link ServletOutputStream}
	 *
	 * @see https://webtide.com/servlet-3-1-async-io-and-jetty/
	 */
	static final class StandardDataStream implements WriteListener {
		private final InputStream content;
		private final AsyncContext async;
		private final ServletOutputStream out;

		private StandardDataStream(InputStream content, AsyncContext async, ServletOutputStream out) {
			this.content = content;
			this.async = async;
			this.out = out;
		}

		@Override
		public void onError(Throwable t) {
			LOG.error("Error streaming from Maven repository", t);
			async.complete();
		}

		@Override
		public void onWritePossible() throws IOException {
			byte[] buffer = new byte[4096];

			// while we are able to write without blocking
			while (out.isReady()) {
				// read some content into the copy buffer
				int len = content.read(buffer);

				// If we are at EOF then complete
				if (len < 0) {
					async.complete();
					return;
				}

				// write out the copy buffer.
				out.write(buffer, 0, len);
			}
		}
	}

	public static final String PROXY_TO = "proxyTo";
	public static final String USERNAME = "username";

	public static final String PASSWORD = "password";

	private static final Logger LOG = LoggerFactory.getLogger(MavenProxyServlet.class);

	/** serialVersionUID */
	private static final long serialVersionUID = 1L;
	private static final Set<String> ALLOWED_HEADERS_TO_COPY = Set.of("accept", "accept-charset", "accept-encoding", "accept-language", "cache-control", "if-match", "if-modified-since", "if-none-match", "if-range", "if-unmodified-since", "range", "te", "transfer-encoding", "user-agent");
	private String targetMavenRepository;

	private Authenticator authenticator;
	private HttpClient httpClient;

	private MavenRepositoryCache mavenCache;

	private void copyHeaders(HttpServletRequest clientRequest, java.net.http.HttpRequest.Builder requestBuilder) {
		Enumeration<String> headerNames = clientRequest.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String header = headerNames.nextElement();
			// copy only approved/allowed headers
			if (ALLOWED_HEADERS_TO_COPY.contains(header.toLowerCase(Locale.ENGLISH))) {
				Enumeration<String> values = clientRequest.getHeaders(header);
				while (values.hasMoreElements()) {
					requestBuilder.header(header, values.nextElement());
				}
			}
		}
	}

	private void copyHeaders(HttpServletResponse clientResponse, HttpResponse<?> response) {
		response.headers().map().forEach((name, values) -> {
			if (ALLOWED_HEADERS_TO_COPY.contains(name.toLowerCase(Locale.ENGLISH))) {
				values.forEach(value -> clientResponse.addHeader(name, value));
			}
		});
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String path = request.getPathInfo();
		File cachedArtifact = findInLocalCache(path);
		if (cachedArtifact != null) {
			streamCachedArtifact(request, response, cachedArtifact);
			return;
		}

		proxy(request, response);
	}

	private File findInLocalCache(String path) {
		if ((mavenCache == null) || (path == null) || path.isBlank() || path.equals("/"))
			return null;

		if (path.startsWith("/")) {
			path = path.substring(1);
		}

		return mavenCache.get(get(path));
	}

	private HttpOutput getJettyServletOutputStream(HttpServletResponse response) throws IOException {
		// this will fail when deployed outside of Jetty, which isn't supported
		return (HttpOutput) response.getOutputStream();
	}

	protected int getRequestId(HttpServletRequest clientRequest) {
		return System.identityHashCode(clientRequest);
	}

	@Override
	public void init() throws ServletException {
		targetMavenRepository = getServletConfig().getInitParameter(PROXY_TO);
		if (targetMavenRepository == null)
			throw new UnavailableException("Init parameter 'proxyTo' is required.");

		mavenCache = (MavenRepositoryCache) getServletConfig().getServletContext().getAttribute(MavenRepositoryCache.class.getName());

		String username = getServletConfig().getInitParameter(USERNAME);
		String password = getServletConfig().getInitParameter(PASSWORD);
		if ((username != null) && (password != null)) {
			PasswordAuthentication passwordAuthentication = new PasswordAuthentication(username, password.toCharArray());
			authenticator = new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return passwordAuthentication;
				}
			};
		}

		Builder httpClientBuilder = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).connectTimeout(Duration.ofSeconds(5));
		if (authenticator != null) {
			LOG.debug("Using autentication: {}", authenticator);
			httpClientBuilder.authenticator(authenticator);
		}

		// if the JVM is not instructed to use system settings on purpose we
		// replicate Bazel behavior, i.e.
		// we read proxy settings from the environment
		if (!Boolean.getBoolean("java.net.useSystemProxies")) {
			ProxyHelper proxyHelper = new ProxyHelper(System.getenv());
			LOG.debug("Configuring proxy selector from environment variables: {}", proxyHelper);
			httpClientBuilder.proxy(new ProxySelector() {

				@Override
				public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
					LOG.debug("Connect failed: {} {} - {}", uri, sa, ioe.getMessage(), ioe);
				}

				@Override
				public List<Proxy> select(URI uri) {
					try {
						Proxy proxy = proxyHelper.createProxyIfNeeded(uri);
						if (proxy != Proxy.NO_PROXY) {
							LOG.debug("Using proxy '{}' for URI '{}'", proxy, uri);
						} else {
							LOG.debug("Using direct connection to URI '{}'", uri);
						}
						return List.of(proxy);
					} catch (IOException e) {
						throw new IllegalStateException("Unable to create proxy!", e);
					}
				}
			});
		}

		httpClient = httpClientBuilder.build();
	}

	private void proxy(HttpServletRequest clientRequest, HttpServletResponse clientResponse) throws ServletException, IOException {
		String rewrittenTarget = rewriteTarget(clientRequest);
		if (LOG.isDebugEnabled()) {
			StringBuffer target = clientRequest.getRequestURL();
			if (clientRequest.getQueryString() != null) {
				target.append("?").append(clientRequest.getQueryString());
			}
			LOG.debug("{} rewriting: {} -> {}", getRequestId(clientRequest), target, rewrittenTarget);
		}

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().method(clientRequest.getMethod(), BodyPublishers.noBody()).uri(URI.create(rewrittenTarget)).timeout(Duration.ofSeconds(10));
		copyHeaders(clientRequest, requestBuilder);

		sendProxyRequest(clientRequest, clientResponse, requestBuilder.build());
	}

	private String rewriteTarget(HttpServletRequest clientRequest) throws ServletException {
		StringBuilder uri = new StringBuilder(targetMavenRepository);
		if (uri.charAt(uri.length() - 1) == '/') {
			uri.setLength(uri.length() - 1);
		}

		String pathInfo = clientRequest.getPathInfo();
		if (pathInfo != null) {
			uri.append(pathInfo);
		}

		String queryString = clientRequest.getQueryString();
		if (queryString != null) {
			uri.append('?').append(queryString);
		}

		return uri.toString();
	}

	private void sendProxyRequest(HttpServletRequest clientRequest, HttpServletResponse clientResponse, HttpRequest proxyRequest) throws IOException {
		if (LOG.isDebugEnabled()) {
			StringBuilder clientRequestInfo = new StringBuilder(clientRequest.getMethod());
			clientRequestInfo.append(" ").append(clientRequest.getRequestURI());
			String clientRequestQueryString = clientRequest.getQueryString();
			if (clientRequestQueryString != null) {
				clientRequestInfo.append("?").append(clientRequestQueryString);
			}
			clientRequestInfo.append(" ").append(clientRequest.getProtocol()).append(System.lineSeparator());
			for (Enumeration<String> headerNames = clientRequest.getHeaderNames(); headerNames.hasMoreElements();) {
				String headerName = headerNames.nextElement();
				clientRequestInfo.append(headerName).append(": ");
				for (Enumeration<String> headerValues = clientRequest.getHeaders(headerName); headerValues.hasMoreElements();) {
					String headerValue = headerValues.nextElement();
					if (headerValue != null) {
						clientRequestInfo.append(headerValue);
					}
					if (headerValues.hasMoreElements()) {
						clientRequestInfo.append(",");
					}
				}
				clientRequestInfo.append(System.lineSeparator());
			}
			clientRequestInfo.append(System.lineSeparator());

			StringBuilder proxyRequestInfo = new StringBuilder(proxyRequest.method());
			proxyRequestInfo.append(" ").append(proxyRequest.uri());
			String proxyRequestQueryString = clientRequest.getQueryString();
			if (proxyRequestQueryString != null) {
				proxyRequestInfo.append("?").append(proxyRequestQueryString);
			}
			proxyRequestInfo.append(System.lineSeparator());
			proxyRequest.headers().map().entrySet().forEach(header -> {
				proxyRequestInfo.append(header.getKey()).append(": ");
				for (Iterator<String> values = header.getValue().iterator(); values.hasNext();) {
					String value = values.next();
					if (value != null) {
						proxyRequestInfo.append(value);
					}
					if (values.hasNext()) {
						proxyRequestInfo.append(",");
					}
				}
				proxyRequestInfo.append(System.lineSeparator());
			});
			proxyRequestInfo.append(System.lineSeparator());

			LOG.debug("{} proxying to upstream:{}{}{}", getRequestId(clientRequest), System.lineSeparator(), clientRequestInfo, proxyRequestInfo);
		}

		// we do not timeout the continuation, but the proxy request.
		final AsyncContext asyncContext = clientRequest.startAsync();
		asyncContext.setTimeout(0);

		ServletOutputStream clientOutputStream = clientResponse.getOutputStream();

		if ("HEAD".equals(proxyRequest.method())) {
			httpClient.sendAsync(proxyRequest, BodyHandlers.discarding()).whenComplete((response, e) -> {
				try {
					if (e != null) {
						LOG.error("Error connecting to Maven repository {}: {}", proxyRequest.uri(), e.getMessage(), e);
						try {
							clientResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500, "Unable to connect to target Maven repository.");
						} catch (IOException ioException) {
							if (LOG.isDebugEnabled()) {
								LOG.debug("Exception writing to client", ioException);
							}
						}
					} else {
						clientResponse.setStatus(response.statusCode());
						copyHeaders(clientResponse, response);
					}
				} finally {
					asyncContext.complete();
				}
			});
		} else {
			httpClient.sendAsync(proxyRequest, BodyHandlers.ofInputStream()).thenAccept(response -> {
				clientResponse.setStatus(response.statusCode());
				copyHeaders(clientResponse, response);

				InputStream body = response.body();
				clientOutputStream.setWriteListener(new StandardDataStream(body, asyncContext, clientOutputStream));
			}).exceptionally(e -> {
				LOG.error("Error connecting to Maven repository {}: {}", proxyRequest.uri(), e.getMessage(), e);
				try {
					if (!clientResponse.isCommitted()) {
						clientResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500, "Unable to connect to target Maven repository.");
					}
				} catch (IOException ioException) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Exception writing to client", ioException);
					}
				} finally {
					asyncContext.complete();
				}
				return null;
			});
		}

	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// only GET/HEAD supported
		String method = req.getMethod();
		if ("HEAD".equals(method)) {
			// FIXME: should be smart about HEAD too
			proxy(req, resp);
		} else if ("GET".equals(method)) {
			// FIXME: should support if-modified of super implementation
			doGet(req, resp);
		} else {
			super.service(req, resp);
		}
	}

	private void streamCachedArtifact(HttpServletRequest request, HttpServletResponse response, File cachedArtifact) throws IOException, FileNotFoundException {
		// inspired heavily by:
		// https://webtide.com/servlet-3-1-async-io-and-jetty/
		if (LOG.isDebugEnabled()) {
			LOG.debug("Streaming cached artifact '{}'", cachedArtifact);
		}
		response.setContentType(getServletContext().getMimeType(cachedArtifact.getAbsolutePath()));
		response.setContentLengthLong(cachedArtifact.length());

		ByteBuffer filedMappedBuffer;
		try (RandomAccessFile raf = new RandomAccessFile(cachedArtifact, "r")) {
			filedMappedBuffer = raf.getChannel().map(MapMode.READ_ONLY, 0, raf.length());
		}

		// write the buffer asynchronously
		final ByteBuffer content = filedMappedBuffer.asReadOnlyBuffer();
		final HttpOutput out = getJettyServletOutputStream(response);
		final AsyncContext async = request.startAsync();
		out.setWriteListener(new WriteListener() {
			@Override
			public void onError(Throwable t) {
				getServletContext().log(format("Error while streaming Maven artifact '%s' from cache: %s", cachedArtifact, t.getMessage()), t);
				async.complete();
			}

			@Override
			public void onWritePossible() throws IOException {
				while (out.isReady()) {
					if (!content.hasRemaining()) {
						async.complete();
						return;
					}

					out.write(content);
				}
			}
		});
	}

}
