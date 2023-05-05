package org.idp.server.oauth.identity;

import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.AuthorizationCode;
import org.idp.server.type.oauth.State;
import org.idp.server.type.oidc.Nonce;

public class IdTokenCustomClaims {
  AuthorizationCode authorizationCode;
  AccessTokenValue accessTokenValue;
  State state;
  Nonce nonce;
  CustomProperties customProperties;

  IdTokenCustomClaims(
      AuthorizationCode authorizationCode,
      AccessTokenValue accessTokenValue,
      State state,
      Nonce nonce,
      CustomProperties customProperties) {
    this.authorizationCode = authorizationCode;
    this.accessTokenValue = accessTokenValue;
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

  public AccessTokenValue accessTokenValue() {
    return accessTokenValue;
  }

  public boolean hasAccessTokenValue() {
    return accessTokenValue.exists();
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
