package org.idp.server.oauth.identity;

import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.AuthorizationCode;
import org.idp.server.type.oauth.State;
import org.idp.server.type.oidc.Nonce;

public class IdTokenCustomClaimsBuilder {
  AuthorizationCode authorizationCode = new AuthorizationCode();
  AccessTokenValue accessTokenValue = new AccessTokenValue();
  State state = new State();
  Nonce nonce = new Nonce();
  CustomProperties customProperties = new CustomProperties();

  public IdTokenCustomClaimsBuilder() {}

  public IdTokenCustomClaimsBuilder add(AuthorizationCode authorizationCode) {
    this.authorizationCode = authorizationCode;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(AccessTokenValue accessTokenValue) {
    this.accessTokenValue = accessTokenValue;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(State state) {
    this.state = state;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(Nonce nonce) {
    this.nonce = nonce;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(CustomProperties customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public IdTokenCustomClaims build() {
    return new IdTokenCustomClaims(
        authorizationCode, accessTokenValue, state, nonce, customProperties);
  }
}
