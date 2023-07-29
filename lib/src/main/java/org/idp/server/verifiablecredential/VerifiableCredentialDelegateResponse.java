package org.idp.server.verifiablecredential;

import org.idp.server.basic.vc.VerifiableCredential;

public class VerifiableCredentialDelegateResponse {
  VerifiableCredentialTransactionStatus status;
  VerifiableCredential credential;

  VerifiableCredentialDelegateResponse(
      VerifiableCredentialTransactionStatus status, VerifiableCredential credential) {
    this.status = status;
    this.credential = credential;
  }

  public static VerifiableCredentialDelegateResponse issued(VerifiableCredential credential) {
    return new VerifiableCredentialDelegateResponse(
        VerifiableCredentialTransactionStatus.issued, credential);
  }

  public static VerifiableCredentialDelegateResponse pending() {
    return new VerifiableCredentialDelegateResponse(
        VerifiableCredentialTransactionStatus.pending, new VerifiableCredential());
  }

  public VerifiableCredentialTransactionStatus status() {
    return status;
  }

  public VerifiableCredential credential() {
    return credential;
  }

  public boolean isPending() {
    return status.isPending();
  }

  public boolean isIssued() {
    return status.isIssued();
  }
}
