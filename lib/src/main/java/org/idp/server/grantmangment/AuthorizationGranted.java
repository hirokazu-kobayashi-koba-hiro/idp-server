package org.idp.server.grantmangment;

import org.idp.server.oauth.grant.AuthorizationGrant;

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
}
