package com.carddemo.batch.orchestration.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/**
 * The FTPJCL SYSIN carried user and password in clear; here they live in AWS Secrets Manager (JSON
 * {@code {"accessKeyId":..,"secretAccessKey":..}}) and are read at use time, never logged.
 */
public class SecretsManagerCredentialsProvider implements AwsCredentialsProvider, AutoCloseable {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SecretsManagerClient secrets;
  private final String secretId;

  public SecretsManagerCredentialsProvider(SecretsManagerClient secrets, String secretId) {
    this.secrets = secrets;
    this.secretId = secretId;
  }

  @Override
  public AwsCredentials resolveCredentials() {
    String value =
        secrets
            .getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build())
            .secretString();
    try {
      JsonNode node = JSON.readTree(value);
      String id = node.path("accessKeyId").asText(null);
      String key = node.path("secretAccessKey").asText(null);
      if (id == null || key == null) {
        throw new IllegalStateException(
            "secret " + secretId + " lacks accessKeyId/secretAccessKey");
      }
      return AwsBasicCredentials.create(id, key);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("secret " + secretId + " is not JSON", e);
    }
  }

  @Override
  public void close() {
    secrets.close();
  }
}
