package org.idp.server.io;

import java.util.Map;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.status.OAuthRequestStatus;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestStatus status;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  Map<String, String> contents;

  public OAuthRequestResponse() {}

  public OAuthRequestResponse(OAuthRequestStatus status) {
    this.status = status;
  }

  public OAuthRequestResponse(
      OAuthRequestStatus status,
      AuthorizationRequest authorizationRequest,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.status = status;
    this.authorizationRequest = authorizationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public OAuthRequestResponse(OAuthRequestStatus status, OAuthRequestContext context) {
    this.status = status;
    this.authorizationRequest = context.authorizationRequest();
    this.serverConfiguration = context.serverConfiguration();
    this.clientConfiguration = context.clientConfiguration();
    this.contents = Map.of("id", context.identifier().value());
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
