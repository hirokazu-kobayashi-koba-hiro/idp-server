package org.idp.server.oauth.grant;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.oauth.identity.IdTokenClaims;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.oauth.AuthorizationCode;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ExpiredAt;
import org.idp.server.type.oauth.Scopes;

/** AuthorizationCodeGrant */
public class AuthorizationCodeGrant {

  AuthorizationRequestIdentifier authorizationRequestIdentifier =
      new AuthorizationRequestIdentifier();
  AuthorizationGrant authorizationGrant;
  AuthorizationCode authorizationCode;
  ExpiredAt expiredAt;

  public AuthorizationCodeGrant() {}

  public AuthorizationCodeGrant(
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthorizationGrant authorizationGrant,
      AuthorizationCode authorizationCode,
      ExpiredAt expiredAt) {
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.authorizationGrant = authorizationGrant;
    this.authorizationCode = authorizationCode;
    this.expiredAt = expiredAt;
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequestIdentifier;
  }

  public User user() {
    return authorizationGrant.user();
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean isGrantedClient(ClientId clientId) {
    return authorizationGrant.isGranted(clientId);
  }

  public boolean isExpire(LocalDateTime other) {
    return expiredAt.isExpire(other);
  }

  public boolean exists() {
    return Objects.nonNull(authorizationCode) && authorizationCode.exists();
  }

  public Scopes scopes() {
    return authorizationGrant.scopes();
  }

  public IdTokenClaims idTokenClaims() {
    return authorizationGrant.idTokenClaims();
  }
}
