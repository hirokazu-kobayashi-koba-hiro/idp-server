/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.device;

import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationTransaction;

public class AuthenticationTransactionFindingResponse {
  int statusCode;
  AuthenticationTransaction authenticationTransaction;

  public static AuthenticationTransactionFindingResponse success(
      AuthenticationTransaction authenticationTransaction) {
    return new AuthenticationTransactionFindingResponse(200, authenticationTransaction);
  }

  public static AuthenticationTransactionFindingResponse notFound() {
    return new AuthenticationTransactionFindingResponse(404, null);
  }

  private AuthenticationTransactionFindingResponse(
      int statusCode, AuthenticationTransaction authenticationTransaction) {
    this.statusCode = statusCode;
    this.authenticationTransaction = authenticationTransaction;
  }

  public int statusCode() {
    return statusCode;
  }

  public AuthenticationTransaction authenticationTransaction() {
    return authenticationTransaction;
  }

  public Map<String, Object> contents() {
    if (statusCode == 200) {
      return authenticationTransaction.toMap();
    }
    return Map.of();
  }
}
