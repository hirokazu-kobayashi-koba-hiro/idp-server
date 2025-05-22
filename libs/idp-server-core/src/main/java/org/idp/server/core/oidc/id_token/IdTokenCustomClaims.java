/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.id_token;

import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.basic.type.oauth.State;
import org.idp.server.basic.type.oidc.Nonce;

public class IdTokenCustomClaims {
  AuthorizationCode authorizationCode;
  AccessTokenEntity accessTokenEntity;
  State state;
  Nonce nonce;
  CustomProperties customProperties;

  IdTokenCustomClaims(
      AuthorizationCode authorizationCode,
      AccessTokenEntity accessTokenEntity,
      State state,
      Nonce nonce,
      CustomProperties customProperties) {
    this.authorizationCode = authorizationCode;
    this.accessTokenEntity = accessTokenEntity;
    this.state = state;
    this.nonce = nonce;
    this.customProperties = customProperties;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean hasAuthorizationCode() {
    return authorizationCode.exists();
  }

  public AccessTokenEntity accessTokenValue() {
    return accessTokenEntity;
  }

  public boolean hasAccessTokenValue() {
    return accessTokenEntity.exists();
  }

  public State state() {
    return state;
  }

  public boolean hasState() {
    return state.exists();
  }

  public Nonce nonce() {
    return nonce;
  }

  public boolean hasNonce() {
    return nonce.exists();
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public boolean hasCustomProperties() {
    return customProperties.exists();
  }
}
