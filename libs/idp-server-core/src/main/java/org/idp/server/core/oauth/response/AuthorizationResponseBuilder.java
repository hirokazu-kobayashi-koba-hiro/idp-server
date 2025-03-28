package org.idp.server.core.oauth.response;

import org.idp.server.core.basic.http.QueryParams;
import org.idp.server.core.oauth.token.AccessToken;
import org.idp.server.core.type.extension.JarmPayload;
import org.idp.server.core.type.extension.ResponseModeValue;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.IdToken;
import org.idp.server.core.type.oidc.ResponseMode;
import org.idp.server.core.type.verifiablepresentation.VpToken;

public class AuthorizationResponseBuilder {
  RedirectUri redirectUri;
  ResponseMode responseMode;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode = new AuthorizationCode();
  State state = new State();
  AccessToken accessToken = new AccessToken();
  TokenType tokenType = TokenType.undefined;
  ExpiresIn expiresIn = new ExpiresIn();
  Scopes scopes = new Scopes();
  IdToken idToken = new IdToken();
  VpToken vpToken = new VpToken();
  TokenIssuer tokenIssuer;
  JarmPayload jarmPayload = new JarmPayload();
  QueryParams queryParams;

  public AuthorizationResponseBuilder(
      RedirectUri redirectUri,
      ResponseMode responseMode,
      ResponseModeValue responseModeValue,
      TokenIssuer tokenIssuer) {
    this.redirectUri = redirectUri;
    this.responseMode = responseMode;
    this.responseModeValue = responseModeValue;
    this.tokenIssuer = tokenIssuer;
    this.queryParams = new QueryParams();
    queryParams.add("iss", tokenIssuer.value());
  }

  public AuthorizationResponseBuilder add(AuthorizationCode authorizationCode) {
    this.authorizationCode = authorizationCode;
    this.queryParams.add("code", authorizationCode.value());
    return this;
  }

  public AuthorizationResponseBuilder add(State state) {
    this.state = state;
    this.queryParams.add("state", state.value());
    return this;
  }

  public AuthorizationResponseBuilder add(AccessToken accessToken) {
    this.accessToken = accessToken;
    this.queryParams.add("access_token", accessToken.accessTokenEntity().value());
    return this;
  }

  public AuthorizationResponseBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    this.queryParams.add("expires_in", expiresIn.toStringValue());
    return this;
  }

  public AuthorizationResponseBuilder add(TokenType tokenType) {
    if (tokenType.isDefined()) {
      this.tokenType = tokenType;
      this.queryParams.add("token_type", tokenType.name());
    }
    return this;
  }

  public AuthorizationResponseBuilder add(Scopes scopes) {
    this.scopes = scopes;
    this.queryParams.add("scope", scopes.toStringValues());
    return this;
  }

  public AuthorizationResponseBuilder add(IdToken idToken) {
    this.idToken = idToken;
    this.queryParams.add("id_token", idToken.value());
    return this;
  }

  public AuthorizationResponseBuilder add(VpToken vpToken) {
    this.vpToken = vpToken;
    this.queryParams.add("vp_token", vpToken.value());
    return this;
  }

  public AuthorizationResponseBuilder add(JarmPayload jarmPayload) {
    this.jarmPayload = jarmPayload;
    return this;
  }

  public AuthorizationResponse build() {
    // TODO consider
    if (jarmPayload.exists()) {
      this.queryParams = new QueryParams();
      this.queryParams.add("response", jarmPayload.value());
    }
    return new AuthorizationResponse(
        redirectUri,
        responseMode,
        responseModeValue,
        authorizationCode,
        state,
        accessToken,
        tokenType,
        expiresIn,
        scopes,
        idToken,
        tokenIssuer,
        jarmPayload,
        queryParams);
  }
}
