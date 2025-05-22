/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.authentication;

public enum AuthenticationInteractionStatus {
  SUCCESS(200),
  CLIENT_ERROR(400),
  SERVER_ERROR(500);

  int statusCode;

  AuthenticationInteractionStatus(int statusCode) {
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  public boolean isError() {
    return this == CLIENT_ERROR || this == SERVER_ERROR;
  }
}
