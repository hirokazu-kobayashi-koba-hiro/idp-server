package org.idp.server.token;

import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;
import org.idp.server.type.verifiablecredential.CNonce;
import org.idp.server.type.verifiablecredential.CNonceExpiresIn;

public class OAuthTokenBuilder {
  OAuthTokenIdentifier identifier;
  AccessToken accessToken = new AccessToken();
  RefreshToken refreshToken = new RefreshToken();
  IdToken idToken = new IdToken();
  CNonce cNonce = new CNonce();
  CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn();

  public OAuthTokenBuilder(OAuthTokenIdentifier identifier) {
    this.identifier = identifier;
  }

  public OAuthTokenBuilder add(AccessToken accessToken) {
    this.accessToken = accessToken;
    return this;
  }

  public OAuthTokenBuilder add(RefreshToken refreshToken) {
    this.refreshToken = refreshToken;
    return this;
  }

  public OAuthTokenBuilder add(IdToken idToken) {
    this.idToken = idToken;
    return this;
  }

  public OAuthTokenBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    return this;
  }

  public OAuthTokenBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    return this;
  }

  public OAuthToken build() {
    return new OAuthToken(identifier, accessToken, refreshToken, idToken, cNonce, cNonceExpiresIn);
  }
}
