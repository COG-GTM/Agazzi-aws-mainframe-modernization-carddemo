package com.carddemo.ext.authorization.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnloadPublicationTest {

  @TempDir Path dir;

  @Test
  void publishesBothFilesWithoutLeavingStagingFiles() throws IOException {
    Path roots = dir.resolve("roots");
    Path children = dir.resolve("roots.tmp");
    AuthorizationBatchConfiguration.publishPair(roots, new byte[] {1}, children, new byte[] {2});
    assertThat(Files.readAllBytes(roots)).containsExactly(1);
    assertThat(Files.readAllBytes(children)).containsExactly(2);
    try (var files = Files.list(dir)) {
      assertThat(files).hasSize(2);
    }
  }

  @Test
  void restoresPreviousRootsWhenChildrenCannotBeReplaced() throws IOException {
    Path roots = Files.write(dir.resolve("roots"), new byte[] {9});
    Path children = Files.createDirectories(dir.resolve("children"));
    Files.write(children.resolve("keep"), new byte[] {0});
    assertThatThrownBy(
            () ->
                AuthorizationBatchConfiguration.publishPair(
                    roots, new byte[] {1}, children, new byte[] {2}))
        .isInstanceOf(IOException.class);
    assertThat(Files.readAllBytes(roots)).containsExactly(9);
    try (var files = Files.list(dir)) {
      assertThat(files).hasSize(2);
    }
  }

  @Test
  void rejectsSameOutputPath() {
    Path p = dir.resolve("x");
    assertThatThrownBy(
            () -> AuthorizationBatchConfiguration.publishPair(p, new byte[0], p, new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
