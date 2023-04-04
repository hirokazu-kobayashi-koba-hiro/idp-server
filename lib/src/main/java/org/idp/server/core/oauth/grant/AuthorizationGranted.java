package org.idp.server.core.oauth.grant;

import org.idp.server.core.type.ClientId;
import org.idp.server.core.type.Subject;

public class AuthorizationGranted {

  Subject subject;
  ClientId clientId;

  public AuthorizationGranted(Subject subject, ClientId clientId) {
    this.clientId = clientId;
    this.subject = subject;
  }

  public Subject subject() {
    return subject;
  }

  public ClientId clientId() {
    return clientId;
  }

  public boolean isGranted(ClientId clientId) {
    return this.clientId.equals(clientId);
  }
}
