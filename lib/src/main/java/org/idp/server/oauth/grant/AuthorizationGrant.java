package org.idp.server.oauth.grant;

import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.IdTokenClaims;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oauth.Subject;

public class AuthorizationGrant {

  User user;
  Authentication authentication;
  ClientId clientId;
  Scopes scopes;
  ClaimsPayload claimsPayload;
  CustomProperties customProperties;
  AuthorizationDetails authorizationDetails;

  public AuthorizationGrant() {}

  public AuthorizationGrant(
      User user,
      Authentication authentication,
      ClientId clientId,
      Scopes scopes,
      ClaimsPayload claimsPayload,
      CustomProperties customProperties,
      AuthorizationDetails authorizationDetails) {
    this.user = user;
    this.authentication = authentication;
    this.clientId = clientId;
    this.scopes = scopes;
    this.claimsPayload = claimsPayload;
    this.customProperties = customProperties;
    this.authorizationDetails = authorizationDetails;
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
}
