/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.handler.tokenintrospection.io;

public enum TokenIntrospectionRequestStatus {
  OK(200),
  BAD_REQUEST(400),
  INVALID_TOKEN(200),
  EXPIRED_TOKEN(200),
  SERVER_ERROR(500);

  int statusCode;

  TokenIntrospectionRequestStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isOK() {
    return this == OK;
  }

  public boolean isExpired() {
    return this == EXPIRED_TOKEN;
  }
}
