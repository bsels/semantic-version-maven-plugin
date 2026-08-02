package io.github.bsels.semantic.version.template;

import io.github.bsels.semantic.version.utils.ProcessUtils;
import io.github.bsels.semantic.version.utils.Utils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

///
/// Utility class for resolving template definitions in a version-controlled folder.
/// This class provides methods to parse, fetch, or cache template files used for
/// semantic versioning purposes.
///
/// The templates can either be defined locally as a markdown file or remotely referenced
/// and fetched. In the event of a failure while resolving a remote template, the utility
/// attempts to fallback to a cached version, if available.
///
/// This class does not support instantiation.
///
public final class TemplateResolver {

	/// Local template file name inside the versioning folder.
	public static final String TEMPLATE_FILE_NAME = "template.md";

	/// Offline cache file name inside the versioning folder.
	public static final String CACHE_FILE_NAME = ".template.cache.md";

	/// Full commit SHA pattern, unsupported as a remote ref in this version.
	private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-fA-F]{40}");

	///
	/// Constructs a {@code TemplateResolver} instance.
	///
	/// <p>This constructor is private to prevent instantiation, as this class
	/// only contains static utility methods for template resolution tasks.
	///
	private TemplateResolver() {
		// No instance needed
	}

	/// Resolves the template in a versioning folder.
	///
	/// @param log              Maven log; must not be null
	/// @param versioningFolder versioning folder; must not be null
	/// @return resolved definition, or empty when `template.md` does not exist
	/// @throws NullPointerException   if an argument is null
	/// @throws MojoExecutionException if a local file cannot be read
	/// @throws MojoFailureException   if template configuration or remote resolution fails
	public static Optional<TemplateDefinition> resolve(
			Log log,
			Path versioningFolder
	) throws NullPointerException, MojoExecutionException, MojoFailureException {
		Objects.requireNonNull(log, "`log` must not be null");
		Objects.requireNonNull(versioningFolder, "`versioningFolder` must not be null");

		Path templateFile = versioningFolder.resolve(TEMPLATE_FILE_NAME);
		if (!Files.exists(templateFile)) {
			return Optional.empty();
		}
		ParsedTemplate parsed = parseFile(log, templateFile);
		if (parsed instanceof TemplateDefinition definition) {
			return Optional.of(definition);
		}
		return Optional.of(resolveRemote(
				log,
				versioningFolder,
				(RemoteTemplateReference) parsed
		));
	}

	/// Resolves a remote reference and updates or falls back to its cache.
	///
	/// @param log              Maven log
	/// @param versioningFolder versioning folder
	/// @param remote           remote reference
	/// @return resolved remote definition
	/// @throws MojoExecutionException if cache I/O fails
	/// @throws MojoFailureException   if resolution fails
	private static TemplateDefinition resolveRemote(
			Log log,
			Path versioningFolder,
			RemoteTemplateReference remote
	) throws MojoExecutionException, MojoFailureException {
		if (remote.ref() != null && COMMIT_SHA.matcher(remote.ref()).matches()) {
			throw new MojoFailureException(
					"Remote template `ref` must be a branch or tag, not a commit SHA: %s"
							.formatted(remote.ref())
			);
		}

		String content;
		try {
			content = fetchRemoteContent(remote);
		} catch (MojoExecutionException | MojoFailureException e) {
			return resolveFromCache(log, versioningFolder, e);
		}

		ParsedTemplate parsed;
		try {
			parsed = TemplateParser.parse(log, content);
		} catch (MojoFailureException e) {
			throw new MojoFailureException(
					"Remote template `%s` is invalid: %s".formatted(remote.path(), e.getMessage()),
					e
			);
		}
		if (!(parsed instanceof TemplateDefinition definition)) {
			throw new MojoFailureException(
					"Remote template must not reference another remote template (only one level is supported)"
			);
		}
		writeCache(log, versioningFolder, content);
		return definition;
	}

	/// Fetches raw template content from a shallow clone.
	///
	/// @param remote remote reference
	/// @return template content
	/// @throws MojoExecutionException if cloning or temporary-directory I/O fails
	/// @throws MojoFailureException   if the configured path is invalid or missing
	private static String fetchRemoteContent(RemoteTemplateReference remote)
			throws MojoExecutionException, MojoFailureException {
		Path temporaryDirectory;
		try {
			temporaryDirectory = Files.createTempDirectory("semantic-version-template");
		} catch (IOException e) {
			throw new MojoExecutionException(
					"Unable to create temporary directory for remote template",
					e
			);
		}
		try {
			ProcessUtils.gitShallowClone(remote.remote(), remote.ref(), temporaryDirectory);
			Path remoteTemplate = temporaryDirectory.resolve(remote.path()).normalize();
			if (!remoteTemplate.startsWith(temporaryDirectory)
					|| !Files.isRegularFile(remoteTemplate, LinkOption.NOFOLLOW_LINKS)) {
				throw new MojoFailureException(
						"Remote template repository does not contain `%s`".formatted(remote.path())
				);
			}
			return readFile(remoteTemplate);
		} finally {
			deleteRecursively(temporaryDirectory);
		}
	}

	/// Resolves a cached definition after remote fetching failed.
	///
	/// @param log              Maven log
	/// @param versioningFolder versioning folder
	/// @param cause            remote fetch failure
	/// @return cached template definition
	/// @throws MojoExecutionException if cache reading fails
	/// @throws MojoFailureException   if cache is absent or invalid
	private static TemplateDefinition resolveFromCache(
			Log log,
			Path versioningFolder,
			Exception cause
	) throws MojoExecutionException, MojoFailureException {
		Path cacheFile = versioningFolder.resolve(CACHE_FILE_NAME);
		if (!Files.exists(cacheFile)) {
			throw new MojoFailureException(
					"Unable to fetch remote template and no offline cache exists at `%s`: %s"
							.formatted(cacheFile, cause.getMessage()),
					cause
			);
		}
		log.warn(
				"Unable to fetch remote template (%s); using offline cache `%s`"
						.formatted(cause.getMessage(), cacheFile)
		);
		ParsedTemplate parsed = parseFile(log, cacheFile);
		if (!(parsed instanceof TemplateDefinition definition)) {
			throw new MojoFailureException(
					"Cached template at `%s` is not a usable template".formatted(cacheFile)
			);
		}
		return definition;
	}

	/// Parses a template file and includes its path in validation errors.
	///
	/// @param log  Maven log
	/// @param file template file
	/// @return parsed template
	/// @throws MojoExecutionException if reading fails
	/// @throws MojoFailureException   if parsing fails
	private static ParsedTemplate parseFile(
			Log log,
			Path file
	) throws MojoExecutionException, MojoFailureException {
		try {
			return TemplateParser.parse(log, readFile(file));
		} catch (MojoFailureException e) {
			throw new MojoFailureException(
					"Template `%s` is invalid: %s".formatted(file, e.getMessage()),
					e
			);
		}
	}

	/// Writes successful remote content to the cache.
	///
	/// @param log              Maven log
	/// @param versioningFolder versioning folder
	/// @param content          raw remote content
	private static void writeCache(
			Log log,
			Path versioningFolder,
			String content
	) {
		Path cacheFile = versioningFolder.resolve(CACHE_FILE_NAME);
		try {
			Files.createDirectories(versioningFolder);
			Files.writeString(cacheFile, content);
		} catch (IOException e) {
			log.warn("Unable to write template cache `%s`: %s".formatted(cacheFile, e.getMessage()));
		}
	}

	/// Reads a UTF-8 text file.
	///
	/// @param file file to read
	/// @return file content
	/// @throws MojoExecutionException if reading fails
	private static String readFile(Path file) throws MojoExecutionException {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to read `%s`".formatted(file), e);
		}
	}

	/// Deletes a temporary directory tree on a best-effort basis.
	///
	/// @param directory directory to delete
	private static void deleteRecursively(Path directory) {
		try (Stream<Path> paths = Files.walk(directory)) {
			List<Path> sortedPaths = paths.sorted(Comparator.reverseOrder()).toList();
			Utils.deleteFilesIfExists(sortedPaths);
		} catch (IOException ignored) {
			// Best-effort cleanup only
		} catch (MojoExecutionException ignored) {
			// Best-effort cleanup only
		}
	}
}
