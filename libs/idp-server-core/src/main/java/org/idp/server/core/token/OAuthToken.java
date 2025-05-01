package org.idp.server.core.token;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.oidc.token.RefreshToken;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;

public class OAuthToken {
  OAuthTokenIdentifier identifier;
  AccessToken accessToken;
  RefreshToken refreshToken;
  IdToken idToken;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;

  public OAuthToken() {}

  OAuthToken(
      OAuthTokenIdentifier identifier,
      AccessToken accessToken,
      RefreshToken refreshToken,
      IdToken idToken,
      CNonce cNonce,
      CNonceExpiresIn cNonceExpiresIn) {
    this.identifier = identifier;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.idToken = idToken;
    this.cNonce = cNonce;
    this.cNonceExpiresIn = cNonceExpiresIn;
  }

  public OAuthTokenIdentifier identifier() {
    return identifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return accessToken.tenantIdentifier();
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

  public AccessTokenEntity accessTokenEntity() {
    return accessToken.accessTokenEntity();
  }

  public AccessToken accessToken() {
    return accessToken;
  }

  public RefreshToken refreshToken() {
    return refreshToken;
  }

  public RefreshTokenEntity refreshTokenEntity() {
    return refreshToken.refreshTokenEntity();
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

  public CNonce cNonce() {
    return cNonce;
  }

  public boolean hasCNonce() {
    return cNonce.exists();
  }

  public CNonceExpiresIn cNonceExpiresIn() {
    return cNonceExpiresIn;
  }

  public boolean hasCNonceExpiresIn() {
    return cNonceExpiresIn.exists();
  }

  public TokenIssuer tokenIssuer() {
    return accessToken.tokenIssuer();
  }

  public RequestedClientId requestedClientId() {
    return accessToken.requestedClientId();
  }

  public User user() {
    return accessToken.user();
  }

  public Client client() {
    return accessToken.client();
  }

  public String clientName() {
    return accessToken.client().nameValue();
  }

  public List<String> scopeAsList() {
    return accessToken.scopes().toStringList();
  }
}
