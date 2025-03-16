package org.idp.server.core.verifiablecredential;

public enum VerifiableCredentialTransactionStatus {
  pending,
  issued,
  expired;

  public boolean isPending() {
    return this == pending;
  }

  public boolean isIssued() {
    return this == issued;
  }

  public boolean isExpired() {
    return this == expired;
  }
}
