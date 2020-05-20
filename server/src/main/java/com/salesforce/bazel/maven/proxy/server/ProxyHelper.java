// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.salesforce.bazel.maven.proxy.server;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for setting up a proxy server for network communication
 */
public class ProxyHelper {

	private static final Logger LOG = LoggerFactory.getLogger(ProxyHelper.class);

	/**
	 * This method takes a proxyAddress as a String (ex.
	 * http://userId:password@proxyhost.domain.com:8000) and sets JVM arguments
	 * for http and https proxy as well as returns a java.net.Proxy object for
	 * optional use.
	 *
	 * @param proxyAddress
	 *            The fully qualified address of the proxy server
	 * @return Proxy
	 * @throws IOException
	 */
	public static Proxy createProxy(String proxyAddress) throws IOException {
		if (isNullOrEmpty(proxyAddress))
			return Proxy.NO_PROXY;

		// Here there be dragons.
		Pattern urlPattern = Pattern.compile("^(https?)://(([^:@]+?)(?::([^@]+?))?@)?([^:]+)(?::(\\d+))?/?$");
		Matcher matcher = urlPattern.matcher(proxyAddress);
		if (!matcher.matches())
			throw new IOException("Proxy address " + proxyAddress + " is not a valid URL");

		final String protocol = matcher.group(1);
		final String idAndPassword = matcher.group(2);
		final String username = matcher.group(3);
		final String password = matcher.group(4);
		final String hostname = matcher.group(5);
		final String portRaw = matcher.group(6);

		String cleanProxyAddress = proxyAddress;
		if (idAndPassword != null) {
			cleanProxyAddress = proxyAddress.replace(idAndPassword, ""); // Used
																			// to
																			// remove
																			// id+pwd
																			// from
																			// logging
		}

		boolean https;
		switch (protocol) {
		case "https":
			https = true;
			break;
		case "http":
			https = false;
			break;
		default:
			throw new IOException("Invalid proxy protocol for " + cleanProxyAddress);
		}

		int port = https ? 443 : 80; // Default port numbers

		if (portRaw != null) {
			try {
				port = Integer.parseInt(portRaw);
			} catch (NumberFormatException e) {
				throw new IOException("Error parsing proxy port: " + cleanProxyAddress, e);
			}
		}

		if (username != null) {
			if (password == null)
				throw new IOException("No password given for proxy " + cleanProxyAddress);

			// We need to make sure the proxy password is not url encoded; some
			// special characters in
			// proxy passwords require url encoding for shells and other tools
			// to properly consume.
			final String decodedPassword = URLDecoder.decode(password, "UTF-8");
			Authenticator.setDefault(new Authenticator() {
				@Override
				public PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, decodedPassword.toCharArray());
				}
			});
		}

		return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, port));
	}

	private static boolean isNullOrEmpty(String string) {
		return (string == null) || string.isBlank();
	}

	static boolean isProtocol(URI url, String protocol) {
		// An implementation should accept uppercase letters as equivalent to
		// lowercase in scheme names
		// (e.g., allow "HTTP" as well as "http") for the sake of robustness.
		// Quoth RFC3986 § 3.1
		return protocol.equalsIgnoreCase(url.getScheme());
	}

	private final Map<String, String> env;

	/**
	 * Creates new instance.
	 *
	 * @param env
	 *            client environment to check for proxy settings
	 */
	public ProxyHelper(Map<String, String> env) {
		this.env = env;
	}

	/**
	 * This method takes a String for the resource being requested and sets up a
	 * proxy to make the request if HTTP_PROXY and/or HTTPS_PROXY environment
	 * variables are set.
	 *
	 * @param requestedUrl
	 *            remote resource that may need to be retrieved through a proxy
	 * @throws IOException
	 */
	public Proxy createProxyIfNeeded(URI requestedUrl) throws IOException {
		String proxyAddress = null;
		String noProxyUrl = getNoProxy();
		if (!isNullOrEmpty(noProxyUrl)) {
			String[] noProxyUrlArray = noProxyUrl.split(",");
			String requestedHost = requestedUrl.getHost();
			LOG.debug("Checking host '{}' against whitelist '{}'", requestedHost, noProxyUrlArray);
			for (String element : noProxyUrlArray) {
				element = element.strip();
				if (element.startsWith(".")) {
					// This entry applies to sub-domains only.
					if (requestedHost.endsWith(element))
						return Proxy.NO_PROXY;
				} else {
					// This entry applies to the literal hostname and
					// sub-domains.
					if (requestedHost.equals(element) || requestedHost.endsWith("." + element))
						return Proxy.NO_PROXY;
				}
			}
		}
		if (isProtocol(requestedUrl, "https")) {
			proxyAddress = getHttpsProxy();
		} else if (isProtocol(requestedUrl, "http")) {
			proxyAddress = getHttpProxy();
		}
		return createProxy(proxyAddress);
	}

	private String getHttpProxy() {
		String proxyAddress;
		proxyAddress = env.get("http_proxy");
		if (isNullOrEmpty(proxyAddress)) {
			proxyAddress = env.get("HTTP_PROXY");
		}
		return proxyAddress;
	}

	private String getHttpsProxy() {
		String proxyAddress;
		proxyAddress = env.get("https_proxy");
		if (isNullOrEmpty(proxyAddress)) {
			proxyAddress = env.get("HTTPS_PROXY");
		}
		return proxyAddress;
	}

	private String getNoProxy() {
		String noProxyUrl = env.get("no_proxy");
		if (isNullOrEmpty(noProxyUrl)) {
			noProxyUrl = env.get("NO_PROXY");
		}
		return noProxyUrl;
	}

	@Override
	public String toString() {
		return "ProxyHelper[no_proxy=" + getNoProxy() + ", http_proxy=" + getHttpProxy() + ", https_proxy=" + getHttpsProxy() + "]";
	}
}