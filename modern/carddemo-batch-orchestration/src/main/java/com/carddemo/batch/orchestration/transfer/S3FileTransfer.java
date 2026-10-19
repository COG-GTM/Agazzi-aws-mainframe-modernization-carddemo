package com.carddemo.batch.orchestration.transfer;

import java.nio.file.Path;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** FTPJCL on AWS: {@code PUT} = {@code s3:PutObject} into {@code <bucket>/<remoteDir>/<name>}. */
public class S3FileTransfer implements FileTransferPort, AutoCloseable {

  private final S3Client s3;
  private final String bucket;

  private final AutoCloseable owned;

  public S3FileTransfer(S3Client s3, String bucket) {
    this(s3, bucket, null);
  }

  /** {@code owned}: an extra client (credentials provider) closed together with the S3 client. */
  public S3FileTransfer(S3Client s3, String bucket, AutoCloseable owned) {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("carddemo.orchestration.transfer.bucket is required");
    }
    this.s3 = s3;
    this.bucket = bucket;
    this.owned = owned;
  }

  @Override
  public void close() throws Exception {
    s3.close();
    if (owned != null) {
      owned.close();
    }
  }

  @Override
  public String put(Path local, String remoteDir, String remoteName) {
    String key = RemotePaths.key(remoteDir, remoteName);
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/plain").build(),
        RequestBody.fromFile(local));
    return "s3://" + bucket + "/" + key;
  }
}
