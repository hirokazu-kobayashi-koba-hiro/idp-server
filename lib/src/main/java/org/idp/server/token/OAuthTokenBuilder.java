package org.idp.server.token;

import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class OAuthTokenBuilder {
  OAuthTokenIdentifier identifier;
  AccessToken accessToken = new AccessToken();
  RefreshToken refreshToken = new RefreshToken();
  IdToken idToken = new IdToken();

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

  public OAuthToken build() {
    return new OAuthToken(identifier, accessToken, refreshToken, idToken);
  }
}
