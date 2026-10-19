package com.carddemo.batch.orchestration.transfer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Development/test transport: the "remote" is a local directory (e.g. a mounted share). */
public class LocalDirectoryFileTransfer implements FileTransferPort {

  private final Path root;

  public LocalDirectoryFileTransfer(Path root) {
    this.root = root;
  }

  @Override
  public String put(Path local, String remoteDir, String remoteName) {
    Path target =
        root.resolve(RemotePaths.relative(remoteDir)).resolve(RemotePaths.name(remoteName));
    try {
      Files.createDirectories(target.getParent());
      Files.copy(local, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return target.toString();
  }
}
