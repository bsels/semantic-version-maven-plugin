package io.github.bsels.semantic.version.parameters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

public class GitTest {

    @Test
    void numberOfEnumElements_Return3() {
        assertThat(Git.values())
                .hasSize(3)
                .extracting(Git::name)
                .containsExactlyInAnyOrder("NO_GIT", "COMMIT", "STASH");
    }

    @ParameterizedTest
    @EnumSource(Git.class)
    void toString_ReturnsCorrectValue(Git git) {
        assertThat(git.toString())
                .isEqualTo(git.name());
    }

    @ParameterizedTest
    @EnumSource(Git.class)
    void valueOf_ReturnCorrectValue(Git git) {
        assertThat(Git.valueOf(git.toString()))
                .isEqualTo(git);
    }

    @ParameterizedTest
    @CsvSource({
            "NO_GIT,false,false",
            "STASH,true,false",
            "COMMIT,true,true"
    })
    void mode_ExpectedValue(Git git, boolean isStash, boolean isCommit) {
        assertThat(git.isStash())
                .isEqualTo(isStash);
        assertThat(git.isCommit())
                .isEqualTo(isCommit);
    }
}
