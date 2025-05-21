package org.idp.server.core.extension.verifiable_credentials;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.verifiablecredential.Format;
import org.idp.server.basic.type.verifiablecredential.TransactionId;

public class BatchVerifiableCredentialResponse {
  Format format;
  VerifiableCredential verifiableCredential;
  TransactionId transactionId;

  public BatchVerifiableCredentialResponse() {}

  public BatchVerifiableCredentialResponse(
      Format format, VerifiableCredential verifiableCredential) {
    this.format = format;
    this.verifiableCredential = verifiableCredential;
  }

  public BatchVerifiableCredentialResponse(TransactionId transactionId) {
    this.transactionId = transactionId;
  }

  public Format getFormat() {
    return format;
  }

  public VerifiableCredential verifiableCredential() {
    return verifiableCredential;
  }

  public TransactionId transactionId() {
    return transactionId;
  }

  public Map<String, Object> toMap() {
    if (Objects.nonNull(transactionId)) {
      return Map.of("transaction_id", transactionId.value());
    }
    return Map.of("format", format.name(), "credential", verifiableCredential.value());
  }
}
