package org.idp.server.core.oidc.grant;

import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.grant.consent.ConsentClaims;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthorizationGrantBuilder {

  TenantIdentifier tenantIdentifier;
  User user = new User();
  Authentication authentication = new Authentication();
  RequestedClientId requestedClientId;
  Client client = new Client();
  GrantType grantType;
  Scopes scopes;
  GrantIdTokenClaims grantIdTokenClaims = new GrantIdTokenClaims();
  GrantUserinfoClaims grantUserinfoClaims = new GrantUserinfoClaims();
  CustomProperties customProperties = new CustomProperties();
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();
  ConsentClaims consentClaims = new ConsentClaims();

  public AuthorizationGrantBuilder(
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      GrantType grantType,
      Scopes scopes) {
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.grantType = grantType;
    this.scopes = scopes;
  }

  public AuthorizationGrantBuilder add(User user) {
    this.user = user;
    return this;
  }

  public AuthorizationGrantBuilder add(Authentication authentication) {
    this.authentication = authentication;
    return this;
  }

  public AuthorizationGrantBuilder add(Client client) {
    this.client = client;
    return this;
  }

  public AuthorizationGrantBuilder add(GrantIdTokenClaims grantIdTokenClaims) {
    this.grantIdTokenClaims = grantIdTokenClaims;
    return this;
  }

  public AuthorizationGrantBuilder add(GrantUserinfoClaims grantUserinfoClaims) {
    this.grantUserinfoClaims = grantUserinfoClaims;
    return this;
  }

  public AuthorizationGrantBuilder add(CustomProperties customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public AuthorizationGrantBuilder add(AuthorizationDetails authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    return this;
  }

  public AuthorizationGrantBuilder add(ConsentClaims consentClaims) {
    this.consentClaims = consentClaims;
    return this;
  }

  public AuthorizationGrant build() {
    return new AuthorizationGrant(
        tenantIdentifier,
        user,
        authentication,
        requestedClientId,
        client,
        grantType,
        scopes,
        grantIdTokenClaims,
        grantUserinfoClaims,
        customProperties,
        authorizationDetails,
        consentClaims);
  }
}
