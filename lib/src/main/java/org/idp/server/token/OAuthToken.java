package org.idp.server.token;

public class OAuthToken {
  TokenResponse tokenResponse;
  AccessTokenPayload accessTokenPayload;

  public OAuthToken() {}

  public OAuthToken(TokenResponse tokenResponse, AccessTokenPayload accessTokenPayload) {
    this.tokenResponse = tokenResponse;
    this.accessTokenPayload = accessTokenPayload;
  }

  public TokenResponse tokenResponse() {
    return tokenResponse;
  }

  public AccessTokenPayload accessTokenPayload() {
    return accessTokenPayload;
  }
}
