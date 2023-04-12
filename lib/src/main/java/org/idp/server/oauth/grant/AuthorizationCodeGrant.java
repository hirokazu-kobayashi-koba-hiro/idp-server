package org.idp.server.oauth.grant;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.oauth.AuthorizationCode;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ExpiredAt;
import org.idp.server.type.oauth.Subject;

/** AuthorizationCodeGrant */
public class AuthorizationCodeGrant {

  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  AuthorizationGranted authorizationGranted;
  AuthorizationCode authorizationCode;
  ExpiredAt expiredAt;

  public AuthorizationCodeGrant() {}

  public AuthorizationCodeGrant(
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthorizationGranted authorizationGranted,
      AuthorizationCode authorizationCode,
      ExpiredAt expiredAt) {
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.authorizationGranted = authorizationGranted;
    this.authorizationCode = authorizationCode;
    this.expiredAt = expiredAt;
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequestIdentifier;
  }

  public Subject subject() {
    return authorizationGranted.subject();
  }

  public AuthorizationGranted authorizationGranted() {
    return authorizationGranted;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean isGrantedClient(ClientId clientId) {
    return authorizationGranted.isGranted(clientId);
  }

  public boolean isExpire(LocalDateTime other) {
    return expiredAt.isExpire(other);
  }

  public boolean exists() {
    return Objects.nonNull(authorizationCode) && authorizationCode.exists();
  }
}
