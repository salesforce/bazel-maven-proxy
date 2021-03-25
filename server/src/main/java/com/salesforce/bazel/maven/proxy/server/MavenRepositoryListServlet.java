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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MavenRepositoryListServlet extends HttpServlet {

	/** map with repository name as key and URL as value */
	public static final String REPOSITORIES_MAP = "repositories";

	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	private Map<String, URL> repositories;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/plain");
		try (PrintWriter writer = response.getWriter()) {
			writer.println("Repository Mappings");
			writer.println("-------------------");
			Map<String, URL> repositories = this.repositories;
			if ((repositories != null) && (repositories.size() > 0)) {
				repositories.forEach((name, url) -> writer.printf("%s --> %s%n", name, url));
			} else {
				writer.println("No repositories configured.");
			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void init() throws ServletException {
		repositories = (Map<String, URL>) getServletContext().getAttribute(REPOSITORIES_MAP);
	}
}
