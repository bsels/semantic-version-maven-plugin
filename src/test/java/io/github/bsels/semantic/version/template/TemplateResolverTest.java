package io.github.bsels.semantic.version.template;

import io.github.bsels.semantic.version.test.utils.TestLog;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class TemplateResolverTest {

	@TempDir
	Path tempDirectory;

	private String readTemplateResource(String fileName) throws IOException {
		try (
				InputStream input = Objects.requireNonNull(
						getClass().getResourceAsStream("/itests/changelog-templates/" + fileName)
				)
		) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private Path versioningFolder() throws Exception {
		return Files.createDirectories(tempDirectory.resolve(".versioning"));
	}

	private Path createRemoteRepository(
			String content,
			String templatePath
	) throws Exception {
		Path source = tempDirectory.resolve("remote-repo");
		Path template = source.resolve(templatePath);
		Files.createDirectories(template.getParent());
		runGit(source, "init", "--initial-branch=main");
		runGit(source, "config", "user.email", "test@example.com");
		runGit(source, "config", "user.name", "Test");
		Files.writeString(template, content);
		runGit(source, "add", ".");
		runGit(source, "-c", "commit.gpgsign=false", "commit", "-m", "add template");
		return source;
	}

	private void runGit(
			Path directory,
			String... arguments
	) throws Exception {
		List<String> command = new ArrayList<>(List.of("git"));
		command.addAll(List.of(arguments));
		Process process = new ProcessBuilder(command)
				.directory(directory.toFile())
				.start();
		assertThat(process.waitFor()).isZero();
	}

	private void writeRemoteReference(
			Path versioningFolder,
			Path remoteRepository,
			String path,
			String ref
	) throws Exception {
		StringBuilder frontMatter = new StringBuilder("---\n");
		frontMatter.append("remote: ").append(remoteRepository.toUri()).append("\n");
		if (path != null) {
			frontMatter.append("path: ").append(path).append("\n");
		}
		if (ref != null) {
			frontMatter.append("ref: ").append(ref).append("\n");
		}
		frontMatter.append("---\n");
		Files.writeString(
				versioningFolder.resolve(TemplateResolver.TEMPLATE_FILE_NAME),
				frontMatter
		);
	}

	@Nested
	class ResolveTest {

		@Test
		void nullLog_ThrowsNullPointerException() {
			assertThatThrownBy(() -> TemplateResolver.resolve(null, tempDirectory))
					.isExactlyInstanceOf(NullPointerException.class)
					.hasMessage("`log` must not be null");
		}

		@Test
		void nullVersioningFolder_ThrowsNullPointerException() {
			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), null))
					.isExactlyInstanceOf(NullPointerException.class)
					.hasMessage("`versioningFolder` must not be null");
		}

		@Test
		void missingTemplate_ReturnsEmpty() throws Exception {
			Optional<TemplateDefinition> result = TemplateResolver.resolve(
					new SystemStreamLog(),
					versioningFolder()
			);

			assertThat(result).isEmpty();
		}

		@Test
		void localTemplate_ReturnsDefinition() throws Exception {
			Path folder = versioningFolder();
			Files.writeString(
					folder.resolve(TemplateResolver.TEMPLATE_FILE_NAME),
					readTemplateResource("local.md")
			);

			Optional<TemplateDefinition> result = TemplateResolver.resolve(
					new SystemStreamLog(),
					folder
			);

			assertThat(result).isPresent();
			assertThat(result.orElseThrow().variables()).extracting(TemplateVariable::name)
					.containsExactly("issueKey", "description");
		}

		@Test
		void unreadableLocalTemplate_ThrowsExecutionException() throws Exception {
			Path folder = versioningFolder();
			Path template = Files.createDirectory(folder.resolve(TemplateResolver.TEMPLATE_FILE_NAME));

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoExecutionException.class)
					.hasMessage("Unable to read `%s`".formatted(template))
					.hasCauseInstanceOf(IOException.class);
		}

		@Test
		void invalidLocalTemplate_ThrowsFailureNamingFile() throws Exception {
			Path folder = versioningFolder();
			Path template = folder.resolve(TemplateResolver.TEMPLATE_FILE_NAME);
			Files.writeString(template, "not a template");

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							(
									"Template `%s` is invalid: "
											+ "Template must start with a YAML front matter block (`---`)"
							).formatted(template)
					)
					.hasCauseInstanceOf(MojoFailureException.class);
		}

		@Test
		void remoteTemplate_FetchesAndCaches() throws Exception {
			Path folder = versioningFolder();
			String template = readTemplateResource("local.md");
			Path remote = createRemoteRepository(template, "changelog-template.md");
			writeRemoteReference(folder, remote, "changelog-template.md", "main");

			Optional<TemplateDefinition> result = TemplateResolver.resolve(
					new SystemStreamLog(),
					folder
			);

			assertThat(result).isPresent();
			assertThat(result.orElseThrow().variables().get(0).name()).isEqualTo("issueKey");
			assertThat(folder.resolve(TemplateResolver.CACHE_FILE_NAME))
					.content()
					.isEqualTo(template);
		}

		@Test
		void remoteTemplate_RemovesTemporaryCloneDirectory() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(folder, remote, "changelog-template.md", "main");
			Path cloneDirectory = tempDirectory.resolve("clone");

			try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
				files.when(() -> Files.createTempDirectory("semantic-version-template"))
						.thenReturn(cloneDirectory);

				assertThat(TemplateResolver.resolve(new SystemStreamLog(), folder)).isPresent();
			}

			assertThat(cloneDirectory).doesNotExist();
		}

		@Test
		void remoteTemplate_DefaultPathAndBranchFetches() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					".versioning/template.md"
			);
			writeRemoteReference(folder, remote, null, null);

			Optional<TemplateDefinition> result = TemplateResolver.resolve(
					new SystemStreamLog(),
					folder
			);

			assertThat(result).isPresent();
			assertThat(result.orElseThrow().variables()).extracting(TemplateVariable::name)
					.containsExactly("issueKey", "description");
		}

		@Test
		void temporaryDirectoryCreationFailure_ThrowsWithoutCache() throws Exception {
			Path folder = versioningFolder();
			Path remote = tempDirectory.resolve("unused-remote");
			writeRemoteReference(folder, remote, "template.md", "main");
			IOException failure = new IOException("temporary storage unavailable");

			Throwable thrown;
			try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
				files.when(() -> Files.createTempDirectory("semantic-version-template"))
						.thenThrow(failure);

				thrown = catchThrowable(() -> TemplateResolver.resolve(new SystemStreamLog(), folder));
			}

			assertThat(thrown)
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageContaining("no offline cache")
					.hasCauseInstanceOf(MojoExecutionException.class);
			assertThat(thrown.getCause())
					.hasMessage("Unable to create temporary directory for remote template")
					.hasCause(failure);
		}

		@Test
		void temporaryDirectoryCleanupFailure_DoesNotFailResolution() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(folder, remote, "changelog-template.md", "main");
			Path cloneDirectory = Files.createDirectory(tempDirectory.resolve("clone"));

			Optional<TemplateDefinition> result;
			try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
				files.when(() -> Files.createTempDirectory("semantic-version-template"))
						.thenReturn(cloneDirectory);
				files.when(() -> Files.walk(cloneDirectory))
						.thenThrow(new IOException("cleanup failed"));

				result = TemplateResolver.resolve(new SystemStreamLog(), folder);
			}

			assertThat(result).isPresent();
			assertThat(folder.resolve(TemplateResolver.CACHE_FILE_NAME)).isRegularFile();
		}

		@Test
		void temporaryFileDeletionFailure_DoesNotFailResolution() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(folder, remote, "changelog-template.md", "main");
			Path cloneDirectory = tempDirectory.resolve("clone");
			IOException failure = new IOException("cleanup failed");

			Optional<TemplateDefinition> result;
			try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
				files.when(() -> Files.createTempDirectory("semantic-version-template"))
						.thenReturn(cloneDirectory);
				files.when(() -> Files.deleteIfExists(cloneDirectory))
						.thenThrow(failure);

				result = TemplateResolver.resolve(new SystemStreamLog(), folder);
			}

			assertThat(result).isPresent();
			assertThat(folder.resolve(TemplateResolver.CACHE_FILE_NAME)).isRegularFile();
		}

		@Test
		void failedRemoteFetch_WithCache_UsesCache() throws Exception {
			Path folder = versioningFolder();
			String template = readTemplateResource("local.md");
			Path remote = createRemoteRepository(template, "changelog-template.md");
			writeRemoteReference(folder, remote, "missing.md", "main");
			Files.writeString(folder.resolve(TemplateResolver.CACHE_FILE_NAME), template);
			TestLog log = new TestLog(TestLog.LogLevel.DEBUG);

			Optional<TemplateDefinition> result = TemplateResolver.resolve(
					log,
					folder
			);

			assertThat(result).isPresent();
			assertThat(result.orElseThrow().variables().get(0).name()).isEqualTo("issueKey");
			assertThat(log.getLogRecords()).anySatisfy(record -> assertThat(record)
					.hasFieldOrPropertyWithValue("level", TestLog.LogLevel.WARN));
		}

		@Test
		void failedClone_WithCacheUsesCache() throws Exception {
			Path folder = versioningFolder();
			Path missingRemote = tempDirectory.resolve("missing-remote");
			writeRemoteReference(folder, missingRemote, "template.md", "main");
			Files.writeString(
					folder.resolve(TemplateResolver.CACHE_FILE_NAME),
					readTemplateResource("local.md")
			);
			TestLog log = new TestLog(TestLog.LogLevel.DEBUG);

			Optional<TemplateDefinition> result = TemplateResolver.resolve(log, folder);

			assertThat(result).isPresent();
			assertThat(log.getLogRecords()).anySatisfy(record -> assertThat(record)
					.hasFieldOrPropertyWithValue("level", TestLog.LogLevel.WARN)
					.extracting(TestLog.LogRecord::message)
					.asString()
					.contains("using offline cache"));
		}

		@Test
		void failedRemoteFetch_WithoutCacheThrows() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(folder, remote, "missing.md", "main");

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageContaining("no offline cache")
					.hasCauseInstanceOf(MojoFailureException.class);
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"0123456789abcdef0123456789abcdef01234567",
				"0123456789ABCDEF0123456789ABCDEF01234567"
		})
		void commitShaRef_Throws(String ref) throws Exception {
			Path folder = versioningFolder();
			Path remote = tempDirectory.resolve("unused-remote");
			writeRemoteReference(
					folder,
					remote,
					"changelog-template.md",
					ref
			);

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Remote template `ref` must be a branch or tag, not a commit SHA: %s"
									.formatted(ref)
					);
		}

		@Test
		void invalidFetchedTemplate_ThrowsFailureNamingRemotePath() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository("not a template", "changelog-template.md");
			writeRemoteReference(folder, remote, "changelog-template.md", "main");

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Remote template `changelog-template.md` is invalid: "
									+ "Template must start with a YAML front matter block (`---`)"
					)
					.hasCauseInstanceOf(MojoFailureException.class);
		}

		@Test
		void nestedRemoteReference_Throws() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					"""
							---
							remote: git@github.com:org/other.git
							---
							""", "changelog-template.md"
			);
			writeRemoteReference(folder, remote, "changelog-template.md", "main");

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							"Remote template must not reference another remote template "
									+ "(only one level is supported)"
					);
		}

		@Test
		void traversalRemotePath_ThrowsWithoutReadingOutsideClone() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(folder, remote, "../outside.md", "main");

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessageContaining("no offline cache")
					.hasRootCauseMessage("Remote template repository does not contain `../outside.md`");
		}

		@Test
		void invalidCache_ThrowsFailureNamingCacheFile() throws Exception {
			Path folder = versioningFolder();
			Path missingRemote = tempDirectory.resolve("missing-remote");
			writeRemoteReference(folder, missingRemote, "template.md", "main");
			Path cache = folder.resolve(TemplateResolver.CACHE_FILE_NAME);
			Files.writeString(cache, "not a template");

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage(
							(
									"Template `%s` is invalid: "
											+ "Template must start with a YAML front matter block (`---`)"
							).formatted(cache)
					)
					.hasCauseInstanceOf(MojoFailureException.class);
		}

		@Test
		void cacheContainingRemoteReference_ThrowsNotUsable() throws Exception {
			Path folder = versioningFolder();
			Path missingRemote = tempDirectory.resolve("missing-remote");
			writeRemoteReference(folder, missingRemote, "template.md", "main");
			Path cache = folder.resolve(TemplateResolver.CACHE_FILE_NAME);
			Files.writeString(cache, readTemplateResource("remote-reference.md"));

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isExactlyInstanceOf(MojoFailureException.class)
					.hasMessage("Cached template at `%s` is not a usable template".formatted(cache));
		}

		@Test
		void cacheWriteFailure_LogsWarningAndReturnsDefinition() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(folder, remote, "changelog-template.md", "main");
			Path cache = Files.createDirectory(folder.resolve(TemplateResolver.CACHE_FILE_NAME));
			TestLog log = new TestLog(TestLog.LogLevel.DEBUG);

			Optional<TemplateDefinition> result = TemplateResolver.resolve(log, folder);

			assertThat(result).isPresent();
			assertThat(log.getLogRecords()).anySatisfy(record -> assertThat(record)
					.hasFieldOrPropertyWithValue("level", TestLog.LogLevel.WARN)
					.extracting(TestLog.LogRecord::message)
					.asString()
					.contains("Unable to write template cache `%s`".formatted(cache)));
		}
	}
}
