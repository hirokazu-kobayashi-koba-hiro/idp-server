package org.idp.server.control_plane.base.verifier;

import java.util.List;

public class VerificationResult {

  boolean valid;
  List<String> errors;

  private VerificationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public static VerificationResult success() {
    return new VerificationResult(true, List.of());
  }

  public static VerificationResult failure(List<String> errors) {
    return new VerificationResult(false, errors);
  }

  public static VerificationResult empty() {
    return new VerificationResult(true, List.of());
  }

  public boolean isValid() {
    return valid;
  }

  public List<String> errors() {
    return errors;
  }
}
