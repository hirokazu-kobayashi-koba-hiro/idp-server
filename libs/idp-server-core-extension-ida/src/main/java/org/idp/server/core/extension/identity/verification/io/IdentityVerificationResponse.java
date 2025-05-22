/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.io;

import java.util.Map;

public class IdentityVerificationResponse {

  IdentityVerificationApplicationStatus status;
  Map<String, Object> response;

  public static IdentityVerificationResponse OK(Map<String, Object> response) {
    return new IdentityVerificationResponse(IdentityVerificationApplicationStatus.OK, response);
  }

  public static IdentityVerificationResponse CLIENT_ERROR(Map<String, Object> response) {
    return new IdentityVerificationResponse(
        IdentityVerificationApplicationStatus.CLIENT_ERROR, response);
  }

  public static IdentityVerificationResponse SERVER_ERROR(Map<String, Object> response) {
    return new IdentityVerificationResponse(
        IdentityVerificationApplicationStatus.SERVER_ERROR, response);
  }

  private IdentityVerificationResponse(
      IdentityVerificationApplicationStatus status, Map<String, Object> response) {
    this.status = status;
    this.response = response;
  }

  public IdentityVerificationApplicationStatus status() {
    return status;
  }

  public Map<String, Object> response() {
    return response;
  }

  public boolean isOK() {
    return status == IdentityVerificationApplicationStatus.OK;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
