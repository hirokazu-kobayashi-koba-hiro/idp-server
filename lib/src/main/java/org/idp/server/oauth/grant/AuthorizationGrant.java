package org.idp.server.oauth.grant;

import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Scopes;
import org.idp.server.type.oauth.Subject;

public class AuthorizationGrant {

  User user;
  ClientId clientId;
  Scopes scopes;
  ClaimsPayload claimsPayload;
  CustomProperties customProperties;

  public AuthorizationGrant(
      User user,
      ClientId clientId,
      Scopes scopes,
      ClaimsPayload claimsPayload,
      CustomProperties customProperties) {
    this.user = user;
    this.clientId = clientId;
    this.scopes = scopes;
    this.claimsPayload = claimsPayload;
    this.customProperties = customProperties;
  }

  public User user() {
    return user;
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

  public boolean isGranted(ClientId clientId) {
    return this.clientId.equals(clientId);
  }

  public boolean hasUser() {
    return user.exists();
  }
}
