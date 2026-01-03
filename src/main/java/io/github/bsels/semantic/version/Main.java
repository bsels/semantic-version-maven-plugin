package io.github.bsels.semantic.version;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws MojoExecutionException, MojoFailureException {
        UpdatePomMojo mojo = new UpdatePomMojo();
        mojo.baseDirectory = Path.of("/mnt/Data/Development/semantic-version-maven-plugin/");
        mojo.execute();
    }
}
