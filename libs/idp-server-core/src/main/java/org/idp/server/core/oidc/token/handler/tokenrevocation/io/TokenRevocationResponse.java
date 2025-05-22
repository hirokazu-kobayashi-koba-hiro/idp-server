/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.handler.tokenrevocation.io;

import java.util.Map;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class TokenRevocationResponse {
  TokenRevocationRequestStatus status;
  OAuthToken oAuthToken;
  Map<String, Object> response;

  public TokenRevocationResponse(
      TokenRevocationRequestStatus status, OAuthToken oAuthToken, Map<String, Object> contents) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.response = contents;
  }

  public TokenRevocationRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public Map<String, Object> response() {
    return response;
  }

  public boolean isOK() {
    return status.isOK();
  }

  public DefaultSecurityEventType securityEventType() {
    if (!isOK()) {
      return DefaultSecurityEventType.revoke_token_failure;
    }

    return DefaultSecurityEventType.revoke_token_success;
  }

  public boolean hasOAuthToken() {
    return oAuthToken != null && oAuthToken.exists();
  }
}
