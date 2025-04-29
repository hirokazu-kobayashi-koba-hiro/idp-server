package org.idp.server.core.identity.verification.application;

public enum IdentityVerificationApplicationStatus {
  REQUESTED("requested"),
  APPLYING("applying"),
  PROCESSING("processing"),
  APPROVED("approved"),
  REJECTED("rejected"),
  EXPIRED("expired"),
  CANCELLED("cancelled"),
  UNDEFINED(""),
  UNKNOWN("unknown");

  String value;

  IdentityVerificationApplicationStatus(String value) {
    this.value = value;
  }

  public static IdentityVerificationApplicationStatus of(String value) {
    for (IdentityVerificationApplicationStatus status :
        IdentityVerificationApplicationStatus.values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    return UNKNOWN;
  }

  public String value() {
    return value;
  }

  public boolean isRunning() {
    return this == REQUESTED || this == APPLYING || this == PROCESSING;
  }
}
