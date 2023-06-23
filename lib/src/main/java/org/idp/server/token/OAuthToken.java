package org.idp.server.token;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class OAuthToken {
  OAuthTokenIdentifier identifier;
  AccessToken accessToken;
  RefreshToken refreshToken;
  IdToken idToken;

  public OAuthToken() {}

  public OAuthToken(
      OAuthTokenIdentifier identifier,
      AccessToken accessToken,
      RefreshToken refreshToken,
      IdToken idToken) {
    this.identifier = identifier;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.idToken = idToken;
  }

  public OAuthTokenIdentifier identifier() {
    return identifier;
  }

  public TokenIssuer tokenIssuer() {
    return accessToken.tokenIssuer();
  }

  public AuthorizationGrant authorizationGrant() {
    return accessToken.authorizationGrant();
  }

  public boolean exists() {
    return Objects.nonNull(identifier) && identifier.exists();
  }

  public boolean isExpire(LocalDateTime other) {
    return accessToken.isExpired(other);
  }

  public AccessTokenValue accessTokenValue() {
    return accessToken.accessTokenValue();
  }

  public AccessToken accessToken() {
    return accessToken;
  }

  public RefreshToken refreshToken() {
    return refreshToken;
  }

  public RefreshTokenValue refreshTokenValue() {
    return refreshToken.refreshTokenValue();
  }

  public IdToken idToken() {
    return idToken;
  }

  public boolean hasRefreshToken() {
    return refreshToken.exists();
  }

  public Subject subject() {
    return authorizationGrant().subject();
  }

  public boolean hasOpenidScope() {
    return authorizationGrant().hasOpenidScope();
  }

  public Scopes scopes() {
    return accessToken.scopes();
  }

  public TokenType tokenType() {
    return accessToken.tokenType();
  }

  public ExpiresIn expiresIn() {
    return accessToken.expiresIn();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationGrant().authorizationDetails();
  }

  public boolean hasIdToken() {
    return idToken.exists();
  }

  public boolean hasClientCertification() {
    return accessToken.hasClientCertification();
  }
}
