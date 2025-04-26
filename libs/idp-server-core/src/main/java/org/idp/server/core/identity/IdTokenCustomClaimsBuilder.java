package org.idp.server.core.identity;

import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.AuthorizationCode;
import org.idp.server.core.type.oauth.State;
import org.idp.server.core.type.oidc.Nonce;

public class IdTokenCustomClaimsBuilder {
  AuthorizationCode authorizationCode = new AuthorizationCode();
  AccessTokenEntity accessTokenEntity = new AccessTokenEntity();
  State state = new State();
  Nonce nonce = new Nonce();
  CustomProperties customProperties = new CustomProperties();

  public IdTokenCustomClaimsBuilder() {}

  public IdTokenCustomClaimsBuilder add(AuthorizationCode authorizationCode) {
    this.authorizationCode = authorizationCode;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(AccessTokenEntity accessTokenEntity) {
    this.accessTokenEntity = accessTokenEntity;
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
        authorizationCode, accessTokenEntity, state, nonce, customProperties);
  }
}
