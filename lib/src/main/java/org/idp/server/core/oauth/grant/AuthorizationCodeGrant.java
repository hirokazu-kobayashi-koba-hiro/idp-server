package org.idp.server.core.oauth.grant;

import java.time.LocalDateTime;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.*;

/** AuthorizationCodeGrant */
public class AuthorizationCodeGrant {

  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  AuthorizationGranted authorizationGranted;
  AuthorizationCode authorizationCode;
  ExpiredAt expiredAt;

  CustomProperties customProperties;

  public AuthorizationCodeGrant() {}

  public AuthorizationCodeGrant(
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthorizationGranted authorizationGranted,
      AuthorizationCode authorizationCode,
      ExpiredAt expiredAt,
      CustomProperties customProperties) {
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.authorizationGranted = authorizationGranted;
    this.authorizationCode = authorizationCode;
    this.expiredAt = expiredAt;
    this.customProperties = customProperties;
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequestIdentifier;
  }

  public Subject subject() {
    return authorizationGranted.subject();
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public boolean isGrantedClient(ClientId clientId) {
    return authorizationGranted.isGranted(clientId);
  }

  public boolean isExpire(LocalDateTime other) {
    return expiredAt.isExpire(other);
  }
}
