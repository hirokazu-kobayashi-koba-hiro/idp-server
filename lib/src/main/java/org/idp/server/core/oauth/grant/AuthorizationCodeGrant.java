package org.idp.server.core.oauth.grant;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.*;

/** AuthorizationCodeGrant */
public class AuthorizationCodeGrant {

  AuthorizationRequestIdentifier authorizationRequestIdentifier =
      new AuthorizationRequestIdentifier("");
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

  public Authentication authentication() {
    return authorizationGrant.authentication();
  }

  public ClientId clientId() {
    return authorizationGrant.clientId();
  }

  public ExpiredAt expiredAt() {
    return expiredAt;
  }
}
