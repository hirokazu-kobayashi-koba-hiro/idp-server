/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.email;

import java.util.Map;

public class EmailVerificationResult {

  boolean result;
  Map<String, Object> response;

  public static EmailVerificationResult success(Map<String, Object> response) {
    return new EmailVerificationResult(true, response);
  }

  public static EmailVerificationResult failure(Map<String, Object> response) {
    return new EmailVerificationResult(false, response);
  }

  public EmailVerificationResult(boolean result, Map<String, Object> response) {
    this.result = result;
    this.response = response;
  }

  public boolean isSuccess() {
    return result;
  }

  public boolean isFailure() {
    return !result;
  }

  public Map<String, Object> response() {
    return response;
  }
}
