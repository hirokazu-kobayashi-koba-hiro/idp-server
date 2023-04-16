package org.idp.server.handler.io;

import java.util.Map;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.io.status.OAuthRequestStatus;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.response.AuthorizationErrorResponse;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestStatus status;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  AuthorizationErrorResponse errorResponse;
  Map<String, String> contents;
  String redirectUri;

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
  }

  public OAuthRequestResponse(
      OAuthRequestStatus status, Error error, ErrorDescription errorDescription) {
    this.status = status;
    this.contents = Map.of("error", error.value(), "error_description", errorDescription.value());
  }

  public OAuthRequestResponse(OAuthRequestStatus status, AuthorizationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
    this.redirectUri = errorResponse.redirectUriValue();
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

  public String redirectUri() {
    return redirectUri;
  }
}
