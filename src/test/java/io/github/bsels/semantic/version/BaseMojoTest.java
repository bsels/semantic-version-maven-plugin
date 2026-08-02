package io.github.bsels.semantic.version;

import io.github.bsels.semantic.version.models.VersionMarkdown;
import io.github.bsels.semantic.version.template.TemplateResolver;
import io.github.bsels.semantic.version.test.utils.TestLog;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseMojoTest {

    @TempDir
    Path versioningFolder;

    @Test
    void getVersionMarkdowns_IgnoresTemplateAndCacheFiles() throws Exception {
        Path versionMarkdown = versioningFolder.resolve("version.md");
        Files.writeString(
                versionMarkdown, """
                        ---
                        'org.example:project': patch
                        ---
                        
                        A version change.
                        """
        );
        Files.writeString(
                versioningFolder.resolve(TemplateResolver.TEMPLATE_FILE_NAME),
                "not a version markdown file"
        );
        Files.writeString(
                versioningFolder.resolve(TemplateResolver.CACHE_FILE_NAME),
                "not a version markdown file"
        );

        MavenProject project = mock(MavenProject.class);
        when(project.getGroupId()).thenReturn("org.example");
        MavenSession session = mock(MavenSession.class);
        when(session.getCurrentProject()).thenReturn(project);
        UpdatePomMojo mojo = new UpdatePomMojo();
        mojo.setLog(new TestLog(TestLog.LogLevel.NONE));
        mojo.session = session;
        mojo.versionDirectory = versioningFolder;

        List<VersionMarkdown> result = mojo.getVersionMarkdowns();

        assertThat(result).extracting(VersionMarkdown::path)
                .containsExactly(versionMarkdown);
    }
}
