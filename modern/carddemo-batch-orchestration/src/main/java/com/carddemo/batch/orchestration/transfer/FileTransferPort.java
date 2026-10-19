package com.carddemo.batch.orchestration.transfer;

import java.nio.file.Path;

/** FTPJCL {@code PUT 'dsn' remote}: ship a local dataset to the partner endpoint. */
public interface FileTransferPort {

  /** Returns the remote location written (bucket key, path). */
  String put(Path local, String remoteDir, String remoteName);
}
