package org.idp.server.io;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.OAuthRequestResult;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestResult result;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;



  public OAuthRequestResponse() {}

  public OAuthRequestResponse(OAuthRequestResult result) {
    this.result = result;
  }

  public OAuthRequestResponse(OAuthRequestResult result, AuthorizationRequest authorizationRequest, ServerConfiguration serverConfiguration, ClientConfiguration clientConfiguration) {
    this.result = result;
    this.authorizationRequest = authorizationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public OAuthRequestResponse(OAuthRequestResult result, OAuthRequestContext context) {
    this.result = result;
    this.authorizationRequest = context.authorizationRequest();
    this.serverConfiguration = context.serverConfiguration();
    this.clientConfiguration = context.clientConfiguration();
  }

  public OAuthRequestResult result() {
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
