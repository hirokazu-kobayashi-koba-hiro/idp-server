package org.idp.server.oauth.grant;

import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.verifiablepresentation.request.PresentationDefinition;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Scopes;

public class AuthorizationGrantBuilder {

  User user = new User();
  Authentication authentication = new Authentication();
  ClientId clientId;
  Scopes scopes;
  ClaimsPayload claimsPayload = new ClaimsPayload();
  CustomProperties customProperties = new CustomProperties();
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();
  PresentationDefinition presentationDefinition = new PresentationDefinition();

  public AuthorizationGrantBuilder(ClientId clientId, Scopes scopes) {
    this.clientId = clientId;
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

  public AuthorizationGrantBuilder add(ClaimsPayload claimsPayload) {
    this.claimsPayload = claimsPayload;
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

  public AuthorizationGrantBuilder add(PresentationDefinition presentationDefinition) {
    this.presentationDefinition = presentationDefinition;
    return this;
  }

  public AuthorizationGrant build() {
    return new AuthorizationGrant(
        user,
        authentication,
        clientId,
        scopes,
        claimsPayload,
        customProperties,
        authorizationDetails,
        presentationDefinition);
  }
}
