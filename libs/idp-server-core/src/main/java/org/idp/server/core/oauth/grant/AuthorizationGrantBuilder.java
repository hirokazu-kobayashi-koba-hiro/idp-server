package org.idp.server.core.oauth.grant;

import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.identity.RequestedClaimsPayload;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Scopes;

public class AuthorizationGrantBuilder {

  TenantIdentifier tenantIdentifier;
  User user = new User();
  Authentication authentication = new Authentication();
  RequestedClientId requestedClientId;
  Client client = new Client();
  Scopes scopes;
  RequestedClaimsPayload requestedClaimsPayload = new RequestedClaimsPayload();
  CustomProperties customProperties = new CustomProperties();
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();
  PresentationDefinition presentationDefinition = new PresentationDefinition();

  public AuthorizationGrantBuilder(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId, Scopes scopes) {
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
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

  public AuthorizationGrantBuilder add(RequestedClaimsPayload requestedClaimsPayload) {
    this.requestedClaimsPayload = requestedClaimsPayload;
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

  public AuthorizationGrant build() {
    return new AuthorizationGrant(
        tenantIdentifier,
        user,
        authentication,
        requestedClientId,
        client,
        scopes,
            requestedClaimsPayload,
        customProperties,
        authorizationDetails);
  }
}
