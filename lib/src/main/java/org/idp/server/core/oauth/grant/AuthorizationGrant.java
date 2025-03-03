package org.idp.server.core.oauth.grant;

import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.ClaimsPayload;
import org.idp.server.core.oauth.identity.IdTokenClaims;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.Scopes;
import org.idp.server.core.type.oauth.Subject;

public class AuthorizationGrant {

  User user;
  Authentication authentication;
  ClientId clientId;
  Scopes scopes;
  ClaimsPayload claimsPayload;
  CustomProperties customProperties;
  AuthorizationDetails authorizationDetails;
  PresentationDefinition presentationDefinition;

  public AuthorizationGrant() {}

  public AuthorizationGrant(
      User user,
      Authentication authentication,
      ClientId clientId,
      Scopes scopes,
      ClaimsPayload claimsPayload,
      CustomProperties customProperties,
      AuthorizationDetails authorizationDetails,
      PresentationDefinition presentationDefinition) {
    this.user = user;
    this.authentication = authentication;
    this.clientId = clientId;
    this.scopes = scopes;
    this.claimsPayload = claimsPayload;
    this.customProperties = customProperties;
    this.authorizationDetails = authorizationDetails;
    this.presentationDefinition = presentationDefinition;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public String subjectValue() {
    return user.sub();
  }

  public Subject subject() {
    return new Subject(user.sub());
  }

  public ClientId clientId() {
    return clientId;
  }

  public String clientIdValue() {
    return clientId.value();
  }

  public Scopes scopes() {
    return scopes;
  }

  public ClaimsPayload claimsPayload() {
    return claimsPayload;
  }

  public String scopesValue() {
    return scopes.toStringValues();
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public boolean hasCustomProperties() {
    return customProperties.exists();
  }

  public boolean isGranted(ClientId clientId) {
    return this.clientId.equals(clientId);
  }

  public boolean hasUser() {
    return user.exists();
  }

  public IdTokenClaims idTokenClaims() {
    return claimsPayload.idToken();
  }

  public boolean hasOpenidScope() {
    return scopes.contains("openid");
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public boolean hasAuthorizationDetails() {
    return authorizationDetails.exists();
  }

  public boolean hasClaim() {
    return claimsPayload.exists();
  }

  public PresentationDefinition presentationDefinition() {
    return presentationDefinition;
  }

  public boolean hasPresentationDefinition() {
    return presentationDefinition.exists();
  }
}
