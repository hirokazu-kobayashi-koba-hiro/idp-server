package org.idp.server.core.oauth.grant;

import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.Scopes;
import org.idp.server.core.type.oauth.Subject;

public class AuthorizationGranted {

  Subject subject;
  ClientId clientId;
  Scopes scopes;
  CustomProperties customProperties;

  public AuthorizationGranted(
      Subject subject, ClientId clientId, Scopes scopes, CustomProperties customProperties) {
    this.clientId = clientId;
    this.subject = subject;
    this.scopes = scopes;
    this.customProperties = customProperties;
  }

  public Subject subject() {
    return subject;
  }

  public String subjectValue() {
    return subject.value();
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

  public String scopesValue() {
    return scopes.toStringValues();
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public boolean isGranted(ClientId clientId) {
    return this.clientId.equals(clientId);
  }
}
