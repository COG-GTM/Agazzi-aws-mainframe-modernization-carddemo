package com.carddemo.batch.orchestration.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.batch.orchestration.core.OrchestrationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

class TransferTest {

  @TempDir Path tmp;

  @Test
  void remotePathsAreNormalisedAndConfined() {
    assertThat(RemotePaths.key("/ftpfolder", "welcome.txt")).isEqualTo("ftpfolder/welcome.txt");
    assertThat(RemotePaths.key("/", "a.txt")).isEqualTo("a.txt");
    assertThat(RemotePaths.key("a/./b//c", "x")).isEqualTo("a/b/c/x");
    assertThat(RemotePaths.relative(null)).isEmpty();
    assertThatThrownBy(() -> RemotePaths.relative("/a/../.."))
        .isInstanceOf(IllegalArgumentException.class);
    for (String bad : new String[] {null, " ", "a/b", "a\\b", ".."}) {
      assertThatThrownBy(() -> RemotePaths.name(bad)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void localTransferCopiesUnderTheRoot() throws Exception {
    Path src = Files.writeString(tmp.resolve("src"), "DATA");
    String where =
        new LocalDirectoryFileTransfer(tmp.resolve("remote")).put(src, "/ftpfolder", "welcome.txt");
    assertThat(Path.of(where)).hasContent("DATA");
  }

  @Test
  void s3TransferPutsTheObject() throws Exception {
    S3Client s3 = mock(S3Client.class);
    Path src = Files.writeString(tmp.resolve("src"), "DATA");
    String where = new S3FileTransfer(s3, "bucket").put(src, "/ftpfolder", "welcome.txt");
    ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(req.capture(), any(RequestBody.class));
    assertThat(req.getValue().bucket()).isEqualTo("bucket");
    assertThat(req.getValue().key()).isEqualTo("ftpfolder/welcome.txt");
    assertThat(where).isEqualTo("s3://bucket/ftpfolder/welcome.txt");
    assertThatThrownBy(() -> new S3FileTransfer(s3, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static SecretsManagerClient secret(String value) {
    SecretsManagerClient sm = mock(SecretsManagerClient.class);
    when(sm.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString(value).build());
    return sm;
  }

  @Test
  void credentialsComeFromSecretsManager() {
    var p =
        new SecretsManagerCredentialsProvider(
            secret("{\"accessKeyId\":\"AK\",\"secretAccessKey\":\"SK\"}"), "id");
    assertThat(p.resolveCredentials().accessKeyId()).isEqualTo("AK");
    assertThat(p.resolveCredentials().secretAccessKey()).isEqualTo("SK");
    assertThatThrownBy(
            () -> new SecretsManagerCredentialsProvider(secret("{}"), "id").resolveCredentials())
        .hasMessageContaining("lacks");
    assertThatThrownBy(
            () -> new SecretsManagerCredentialsProvider(secret("nope"), "id").resolveCredentials())
        .hasMessageContaining("not JSON");
  }

  @Test
  void factorySelectsTheTransport() {
    var cfg = new OrchestrationProperties().getTransfer();
    cfg.setLocalDir(tmp);
    assertThat(FileTransfers.create(cfg)).isInstanceOf(LocalDirectoryFileTransfer.class);
    assertThat(FileTransfers.credentials(cfg)).isInstanceOf(DefaultCredentialsProvider.class);
    cfg.setType("s3");
    cfg.setBucket("b");
    cfg.setRegion("eu-west-1");
    cfg.setEndpoint("http://localhost:4566");
    assertThat(FileTransfers.create(cfg)).isInstanceOf(S3FileTransfer.class);
    cfg.setCredentialsSecretId("carddemo/ftp");
    assertThat(FileTransfers.credentials(cfg))
        .isInstanceOf(SecretsManagerCredentialsProvider.class);
    cfg.setType("ftp");
    assertThatThrownBy(() -> FileTransfers.create(cfg)).hasMessageContaining("local|s3");
  }
}
