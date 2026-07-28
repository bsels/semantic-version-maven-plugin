package io.github.bsels.semantic.version.template;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
		runGit(source, "commit", "-m", "add template");
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
		void failedRemoteFetch_WithCache_UsesCache() throws Exception {
			Path folder = versioningFolder();
			String template = readTemplateResource("local.md");
			Path remote = createRemoteRepository(template, "changelog-template.md");
			writeRemoteReference(folder, remote, "missing.md", "main");
			Files.writeString(folder.resolve(TemplateResolver.CACHE_FILE_NAME), template);

			Optional<TemplateDefinition> result = TemplateResolver.resolve(
					new SystemStreamLog(),
					folder
			);

			assertThat(result).isPresent();
			assertThat(result.orElseThrow().variables().get(0).name()).isEqualTo("issueKey");
		}

		@Test
		void failedRemoteFetch_WithoutCache_Throws() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(folder, remote, "missing.md", "main");

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("no offline cache");
		}

		@Test
		void commitShaRef_Throws() throws Exception {
			Path folder = versioningFolder();
			Path remote = createRemoteRepository(
					readTemplateResource("local.md"),
					"changelog-template.md"
			);
			writeRemoteReference(
					folder,
					remote,
					"changelog-template.md",
					"0123456789abcdef0123456789abcdef01234567"
			);

			assertThatThrownBy(() -> TemplateResolver.resolve(new SystemStreamLog(), folder))
					.isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("branch or tag");
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
					.isInstanceOf(MojoFailureException.class)
					.hasMessageContaining("one level");
		}
	}
}
