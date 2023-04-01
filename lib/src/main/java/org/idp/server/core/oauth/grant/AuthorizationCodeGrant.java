package org.idp.server.core.oauth.grant;

import java.time.LocalDateTime;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.AuthorizationCode;
import org.idp.server.core.type.ClientId;
import org.idp.server.core.type.ExpiresDateTime;

/** AuthorizationCodeGrant */
public class AuthorizationCodeGrant {

  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  AuthorizationGrantedClient authorizationGrantedClient;
  AuthorizationCode authorizationCode;
  ExpiresDateTime expiresDateTime;

  public AuthorizationCodeGrant() {}

  public AuthorizationCodeGrant(
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthorizationCode authorizationCode,
      ExpiresDateTime expiresDateTime) {
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.authorizationCode = authorizationCode;
    this.expiresDateTime = expiresDateTime;
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequestIdentifier;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean isGrantedClient(ClientId clientId) {
    return authorizationGrantedClient.isGranted(clientId);
  }

  public boolean isExpire(LocalDateTime other) {
    return expiresDateTime.isExpire(other);
  }
}
