package org.idp.server.io;

import java.util.Map;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.response.AuthorizationErrorResponse;
import org.idp.server.io.status.OAuthRequestStatus;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestStatus status;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  AuthorizationErrorResponse errorResponse;
  Map<String, String> contents;

  public OAuthRequestResponse() {}

  public OAuthRequestResponse(OAuthRequestStatus status) {
    this.status = status;
  }

  public OAuthRequestResponse(OAuthRequestStatus status, OAuthRequestContext context) {
    this.status = status;
    this.authorizationRequest = context.authorizationRequest();
    this.serverConfiguration = context.serverConfiguration();
    this.clientConfiguration = context.clientConfiguration();
    this.contents = Map.of("id", context.identifier().value());
    this.errorResponse = new AuthorizationErrorResponse();
  }

  public OAuthRequestStatus status() {
    return status;
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

  public Map<String, String> contents() {
    return contents;
  }
}
