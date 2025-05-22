/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
