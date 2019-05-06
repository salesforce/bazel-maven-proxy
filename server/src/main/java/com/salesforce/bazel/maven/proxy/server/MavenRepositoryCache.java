package com.salesforce.bazel.maven.proxy.server;

import static java.lang.String.format;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Path.of;

import java.io.File;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cache to lookup files/resources from the local Maven repository.
 */
public class MavenRepositoryCache {

	private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryCache.class);

	private final Path localRepositoryPath;

	public MavenRepositoryCache(Path localRepositoryPath) {
		if (!isDirectory(localRepositoryPath))
			throw new IllegalArgumentException(format("Not a valid directory '%s'", localRepositoryPath));

		this.localRepositoryPath = localRepositoryPath;
	}

	public File get(Path path) {
		Path entryPath = localRepositoryPath.resolve(sanitize(path));
		if (isRegularFile(entryPath))
			return entryPath.toFile();

		return null;
	}

	public Path getLocalRepositoryPath() {
		return localRepositoryPath;
	}

	private Path sanitize(Path path) {
		// normalize path and make relative to prevent access outside Maven repo
		Path normalized = path.normalize();
		if (normalized.isAbsolute()) {
			normalized = of("/").relativize(normalized);
		}
		if (LOG.isWarnEnabled() && !path.equals(normalized)) {
			LOG.warn("Normalized input {} --> {}", path, normalized);
		}
		return normalized;
	}
}
