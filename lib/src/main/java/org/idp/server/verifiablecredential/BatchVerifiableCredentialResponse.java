package org.idp.server.verifiablecredential;

import java.util.Map;
import java.util.Objects;
import org.idp.server.type.verifiablecredential.Format;
import org.idp.server.type.verifiablecredential.TransactionId;

public class BatchVerifiableCredentialResponse {
  Format format;
  VerifiableCredentialJwt credentialJwt;
  TransactionId transactionId;

  public BatchVerifiableCredentialResponse() {}

  public BatchVerifiableCredentialResponse(Format format, VerifiableCredentialJwt credentialJwt) {
    this.format = format;
    this.credentialJwt = credentialJwt;
  }

  public BatchVerifiableCredentialResponse(TransactionId transactionId) {
    this.transactionId = transactionId;
  }

  public Format getFormat() {
    return format;
  }

  public VerifiableCredentialJwt credentialJwt() {
    return credentialJwt;
  }

  public TransactionId transactionId() {
    return transactionId;
  }

  public Map<String, Object> toMap() {
    if (Objects.nonNull(transactionId)) {
      return Map.of("transaction_id", transactionId.value());
    }
    return Map.of("format", format.name(), "credential", credentialJwt.value());
  }
}
