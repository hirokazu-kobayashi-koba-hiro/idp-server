package org.idp.server.core.grant_management;

import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.GrantIdTokenClaims;
import org.idp.server.core.oidc.grant.GrantUserinfoClaims;
import org.idp.server.core.oidc.grant.consent.ConsentClaims;

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

  public boolean isGrantedScopes(Scopes requestedScopes) {
    return authorizationGrant.isGrantedScopes(requestedScopes);
  }

  public Scopes unauthorizedScopes(Scopes requestedScopes) {
    return authorizationGrant.unauthorizedScopes(requestedScopes);
  }

  public boolean isGrantedClaims(GrantIdTokenClaims requestedIdTokenClaims) {
    return authorizationGrant.isGrantedIdTokenClaims(requestedIdTokenClaims);
  }

  public boolean isGrantedClaims(GrantUserinfoClaims requestedUserinfoClaims) {
    return authorizationGrant.isGrantedUserinfoClaims(requestedUserinfoClaims);
  }

  public GrantIdTokenClaims unauthorizedIdTokenClaims(GrantIdTokenClaims grantIdTokenClaims) {
    return authorizationGrant.unauthorizedIdTokenClaims(grantIdTokenClaims);
  }

  public GrantUserinfoClaims unauthorizedIdTokenClaims(GrantUserinfoClaims grantUserinfoClaims) {
    return authorizationGrant.unauthorizedUserinfoClaims(grantUserinfoClaims);
  }

  public boolean isConsentedClaims(ConsentClaims requestedConsentClaims) {
    return authorizationGrant.isConsentedClaims(requestedConsentClaims);
  }
}
