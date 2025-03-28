package org.idp.server.core.grantmangment;

import org.idp.server.core.oauth.grant.AuthorizationGrant;

public class AuthorizationGranted {
  AuthorizationGrantedIdentifier identifier;
  AuthorizationGrant authorizationGrant;

  public AuthorizationGranted() {}

  public AuthorizationGranted(
      AuthorizationGrantedIdentifier identifier, AuthorizationGrant authorizationGrant) {
    this.identifier = identifier;
    this.authorizationGrant = authorizationGrant;
  }

  public AuthorizationGrantedIdentifier identifier() {
    return identifier;
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public AuthorizationGranted replace(AuthorizationGrant authorizationGrant) {

    return new AuthorizationGranted(identifier, authorizationGrant);
  }

  public AuthorizationGranted merge(AuthorizationGrant newAuthorizationGrant) {

    AuthorizationGrant merged = authorizationGrant.merge(newAuthorizationGrant);
    return new AuthorizationGranted(identifier, merged);
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }
}
