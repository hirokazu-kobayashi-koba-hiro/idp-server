/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc;

/** AuthorizationProfile */
public enum AuthorizationProfile {
  OAUTH2,
  OIDC,
  FAPI_BASELINE,
  FAPI_ADVANCE,
  UNDEFINED;

  public boolean isOAuth2() {
    return this == OAUTH2;
  }

  public boolean isOidc() {
    return this == OIDC;
  }

  public boolean isFapiBaseline() {
    return this == FAPI_BASELINE;
  }

  public boolean isFapiAdvance() {
    return this == FAPI_ADVANCE;
  }

  public boolean isDefined() {
    return this != UNDEFINED;
  }
}
