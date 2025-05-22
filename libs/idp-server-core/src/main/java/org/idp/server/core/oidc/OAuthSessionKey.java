/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc;

import java.io.Serializable;
import java.util.Objects;

public class OAuthSessionKey implements Serializable {
  String tokenIssuer;
  String clientId;

  public OAuthSessionKey(String tokenIssuer, String clientId) {
    this.tokenIssuer = tokenIssuer;
    this.clientId = clientId;
  }

  public static OAuthSessionKey parse(String sessionKey) {
    String[] split = sessionKey.split(":");
    return new OAuthSessionKey(split[0], split[1]);
  }

  public String tokenIssuer() {
    return tokenIssuer;
  }

  public String clientId() {
    return clientId;
  }

  public String key() {
    return tokenIssuer + ":" + clientId;
  }

  public boolean exists() {
    return Objects.nonNull(tokenIssuer) && Objects.nonNull(clientId);
  }
}
