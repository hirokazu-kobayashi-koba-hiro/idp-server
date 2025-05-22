/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.io;

/** OAuthRequestStatus */
public enum OAuthRequestStatus {
  OK,
  OK_SESSION_ENABLE,
  NO_INTERACTION_OK,
  OK_ACCOUNT_CREATION,
  BAD_REQUEST,
  REDIRECABLE_BAD_REQUEST,
  SERVER_ERROR;

  public boolean isSuccess() {
    return this == OK
        || this == OK_SESSION_ENABLE
        || this == NO_INTERACTION_OK
        || this == OK_ACCOUNT_CREATION;
  }
}
