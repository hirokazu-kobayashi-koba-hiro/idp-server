package org.idp.server.io;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.OAuthRequestStatus;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestStatus result;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;



  public OAuthRequestResponse() {}

  public OAuthRequestResponse(OAuthRequestStatus result) {
    this.result = result;
  }

  public OAuthRequestResponse(OAuthRequestStatus result, AuthorizationRequest authorizationRequest, ServerConfiguration serverConfiguration, ClientConfiguration clientConfiguration) {
    this.result = result;
    this.authorizationRequest = authorizationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public OAuthRequestResponse(OAuthRequestStatus result, OAuthRequestContext context) {
    this.result = result;
    this.authorizationRequest = context.authorizationRequest();
    this.serverConfiguration = context.serverConfiguration();
    this.clientConfiguration = context.clientConfiguration();
  }

  public OAuthRequestStatus result() {
    return result;
  }

  public AuthorizationRequest authorizationRequest() {
    return authorizationRequest;
  }

  public ServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }
}
