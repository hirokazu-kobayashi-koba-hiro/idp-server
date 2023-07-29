package org.idp.server.verifiablecredential;

import java.util.Objects;
import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.verifiablecredential.CredentialIssuer;
import org.idp.server.type.verifiablecredential.TransactionId;

public class VerifiableCredentialTransaction {
  TransactionId transactionId;
  CredentialIssuer credentialIssuer;
  ClientId clientId;
  Subject subject;
  VerifiableCredential verifiableCredential;
  VerifiableCredentialTransactionStatus status;

  public VerifiableCredentialTransaction() {}

  public VerifiableCredentialTransaction(
      TransactionId transactionId,
      CredentialIssuer credentialIssuer,
      ClientId clientId,
      Subject subject,
      VerifiableCredential verifiableCredential,
      VerifiableCredentialTransactionStatus status) {
    this.transactionId = transactionId;
    this.credentialIssuer = credentialIssuer;
    this.clientId = clientId;
    this.subject = subject;
    this.verifiableCredential = verifiableCredential;
    this.status = status;
  }

  public TransactionId transactionId() {
    return transactionId;
  }

  public CredentialIssuer credentialIssuer() {
    return credentialIssuer;
  }

  public ClientId clientId() {
    return clientId;
  }

  public Subject subject() {
    return subject;
  }

  public VerifiableCredential verifiableCredential() {
    return verifiableCredential;
  }

  public VerifiableCredentialTransactionStatus status() {
    return status;
  }

  public boolean exists() {
    return Objects.nonNull(transactionId) && transactionId.exists();
  }
}
