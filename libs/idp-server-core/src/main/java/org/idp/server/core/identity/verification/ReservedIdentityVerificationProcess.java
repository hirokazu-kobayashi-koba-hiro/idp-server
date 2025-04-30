package org.idp.server.core.identity.verification;

public enum ReservedIdentityVerificationProcess {
  CALLBACK_EXAMINATION("callback-examination"),
  CALLBACK_RESULT("callback-result");

  private final String value;

  ReservedIdentityVerificationProcess(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public IdentityVerificationProcess toProcess() {
    return new IdentityVerificationProcess(value);
  }
}
