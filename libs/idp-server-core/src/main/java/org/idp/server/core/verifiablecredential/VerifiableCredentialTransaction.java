package org.idp.server.core.verifiablecredential;

import java.util.Objects;
import org.idp.server.core.basic.vc.Credential;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.verifiablecredential.CredentialIssuer;
import org.idp.server.core.type.verifiablecredential.TransactionId;

public class VerifiableCredentialTransaction {
  TransactionId transactionId;
  CredentialIssuer credentialIssuer;
  RequestedClientId requestedClientId;
  Subject subject;
  Credential credential;
  VerifiableCredentialTransactionStatus status;

  public VerifiableCredentialTransaction() {}

  public VerifiableCredentialTransaction(
      TransactionId transactionId,
      CredentialIssuer credentialIssuer,
      RequestedClientId requestedClientId,
      Subject subject,
      Credential credential,
      VerifiableCredentialTransactionStatus status) {
    this.transactionId = transactionId;
    this.credentialIssuer = credentialIssuer;
    this.requestedClientId = requestedClientId;
    this.subject = subject;
    this.credential = credential;
    this.status = status;
  }

  public TransactionId transactionId() {
    return transactionId;
  }

  public CredentialIssuer credentialIssuer() {
    return credentialIssuer;
  }

  public RequestedClientId clientId() {
    return requestedClientId;
  }

  public Subject subject() {
    return subject;
  }

  public Credential verifiableCredential() {
    return credential;
  }

  public VerifiableCredentialTransactionStatus status() {
    return status;
  }

  public boolean exists() {
    return Objects.nonNull(transactionId) && transactionId.exists();
  }
}
