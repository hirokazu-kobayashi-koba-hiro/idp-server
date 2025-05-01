package org.idp.server.core.oidc.identity;

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
