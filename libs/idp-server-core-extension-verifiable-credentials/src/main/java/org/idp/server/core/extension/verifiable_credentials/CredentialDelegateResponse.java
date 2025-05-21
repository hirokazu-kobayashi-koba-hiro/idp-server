package org.idp.server.core.extension.verifiable_credentials;

import org.idp.server.basic.vc.Credential;

public class CredentialDelegateResponse {
  VerifiableCredentialTransactionStatus status;
  Credential credential;

  CredentialDelegateResponse(VerifiableCredentialTransactionStatus status, Credential credential) {
    this.status = status;
    this.credential = credential;
  }

  public static CredentialDelegateResponse issued(Credential credential) {
    return new CredentialDelegateResponse(VerifiableCredentialTransactionStatus.issued, credential);
  }

  public static CredentialDelegateResponse pending() {
    return new CredentialDelegateResponse(
        VerifiableCredentialTransactionStatus.pending, new Credential());
  }

  public VerifiableCredentialTransactionStatus status() {
    return status;
  }

  public Credential credential() {
    return credential;
  }

  public boolean isPending() {
    return status.isPending();
  }

  public boolean isIssued() {
    return status.isIssued();
  }
}
