package org.idp.server.core.handler.federation.io;

import org.idp.server.core.federation.FederationSession;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;

public class FederationCallbackResponse {

  FederationCallbackStatus status;
  FederationSession federationSession;
  User user;

  public FederationCallbackResponse() {}

  public FederationCallbackResponse(FederationCallbackStatus status) {
    this.status = status;
  }

  public FederationCallbackResponse(
      FederationCallbackStatus status, FederationSession federationSession, User user) {
    this.status = status;
    this.federationSession = federationSession;
    this.user = user;
  }

  public FederationCallbackStatus status() {
    return status;
  }

  public FederationSession federationSession() {
    return federationSession;
  }

  public User user() {
    return user;
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return new AuthorizationRequestIdentifier(federationSession.authorizationRequestId());
  }

  public String authorizationRequestId() {
    return federationSession.authorizationRequestId();
  }

  public boolean isError() {
    return status.isError();
  }
}
