package com.carddemo.batch.orchestration.transfer;

import com.carddemo.batch.orchestration.core.OrchestrationProperties;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

/** Picks the FTPJCL transport from {@code carddemo.orchestration.transfer.type}. */
public final class FileTransfers {

  private FileTransfers() {}

  public static FileTransferPort create(OrchestrationProperties.Transfer cfg) {
    return switch (cfg.getType()) {
      case "local" -> new LocalDirectoryFileTransfer(cfg.getLocalDir());
      case "s3" -> new S3FileTransfer(s3(cfg), cfg.getBucket());
      default ->
          throw new IllegalArgumentException(
              "carddemo.orchestration.transfer.type must be local|s3, was " + cfg.getType());
    };
  }

  static S3Client s3(OrchestrationProperties.Transfer cfg) {
    S3ClientBuilder b = S3Client.builder().credentialsProvider(credentials(cfg));
    if (cfg.getRegion() != null) {
      b.region(Region.of(cfg.getRegion()));
    }
    if (cfg.getEndpoint() != null) {
      b.endpointOverride(URI.create(cfg.getEndpoint())).forcePathStyle(true);
    }
    return b.build();
  }

  static AwsCredentialsProvider credentials(OrchestrationProperties.Transfer cfg) {
    if (cfg.getCredentialsSecretId() == null || cfg.getCredentialsSecretId().isBlank()) {
      return DefaultCredentialsProvider.create();
    }
    SecretsManagerClientBuilder sm = SecretsManagerClient.builder();
    if (cfg.getRegion() != null) {
      sm.region(Region.of(cfg.getRegion()));
    }
    if (cfg.getEndpoint() != null) {
      sm.endpointOverride(URI.create(cfg.getEndpoint()));
    }
    return new SecretsManagerCredentialsProvider(sm.build(), cfg.getCredentialsSecretId());
  }
}
