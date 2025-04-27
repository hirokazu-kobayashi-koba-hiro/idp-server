package org.idp.server.core.identity.verification.verifier;

import java.util.List;

public class IdentityVerificationRequestVerificationResult {

  boolean valid;
  List<String> errors;

  private IdentityVerificationRequestVerificationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public static IdentityVerificationRequestVerificationResult empty() {
    return new IdentityVerificationRequestVerificationResult(false, List.of());
  }

  public static IdentityVerificationRequestVerificationResult success() {
    return new IdentityVerificationRequestVerificationResult(true, List.of());
  }

  public static IdentityVerificationRequestVerificationResult failure(List<String> errors) {
    return new IdentityVerificationRequestVerificationResult(false, errors);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isError() {
    return !valid;
  }

  public List<String> errors() {
    return errors;
  }
}
