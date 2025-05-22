/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.io;

public enum OAuthDenyStatus {
  OK,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isOK() {
    return this == OK;
  }

  public boolean isRedirectableBadRequest() {
    return this == REDIRECABLE_BAD_REQUEST;
  }

  public boolean isError() {
    return this == BAD_REQUEST || this == SERVER_ERROR;
  }
}
