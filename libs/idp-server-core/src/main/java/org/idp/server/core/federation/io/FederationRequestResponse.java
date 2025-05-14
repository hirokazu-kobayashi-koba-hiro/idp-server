package org.idp.server.core.federation.io;

import org.idp.server.core.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.core.federation.sso.oidc.OidcSsoSession;

public class FederationRequestResponse {

  FederationRequestStatus status;
  OidcSsoSession oidcSsoSession;
  OidcSsoConfiguration oidcSsoConfiguration;

  public FederationRequestResponse(FederationRequestStatus status) {
    this.status = status;
  }

  public FederationRequestResponse(
      FederationRequestStatus status,
      OidcSsoSession oidcSsoSession,
      OidcSsoConfiguration oidcSsoConfiguration) {
    this.status = status;
    this.oidcSsoSession = oidcSsoSession;
    this.oidcSsoConfiguration = oidcSsoConfiguration;
  }

  public FederationRequestStatus status() {
    return status;
  }

  public OidcSsoSession federationSession() {
    return oidcSsoSession;
  }

  public OidcSsoConfiguration federatableIdProviderConfiguration() {
    return oidcSsoConfiguration;
  }

  public String authorizationRequestUrl() {
    return oidcSsoSession.authorizationRequestUri();
  }
}
