package org.idp.server.core.identity.verification.application;

public enum IdentityVerificationApplicationStatus {
  REQUESTED,
  APPLYING,
  PROCESSING,
  APPROVED,
  REJECTED,
  EXPIRED,
  CANCELLED;

  public boolean isRunning() {
    return this == REQUESTED || this == APPLYING || this == PROCESSING;
  }
}
