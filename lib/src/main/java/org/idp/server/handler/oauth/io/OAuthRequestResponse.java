package org.idp.server.handler.oauth.io;

import java.util.List;
import java.util.Map;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.response.AuthorizationErrorResponse;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestStatus status;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  AuthorizationResponse response;
  OAuthSession session;
  AuthorizationErrorResponse errorResponse;
  Map<String, String> contents;
  String redirectUri;
  String error;
  String errorDescription;
  String sessionKey;

  public OAuthRequestResponse() {}

  public OAuthRequestResponse(
      OAuthRequestStatus status, OAuthRequestContext context, OAuthSession session) {
    this.status = status;
    this.authorizationRequest = context.authorizationRequest();
    this.serverConfiguration = context.serverConfiguration();
    this.clientConfiguration = context.clientConfiguration();
    this.session = session;
    this.contents = Map.of("id", context.identifier().value());
    this.sessionKey = context.sessionKeyValue();
  }

  public OAuthRequestResponse(
      OAuthRequestStatus status, Error error, ErrorDescription errorDescription) {
    this.status = status;
    this.error = error.value();
    this.errorDescription = errorDescription.value();
    this.contents = Map.of("error", error.value(), "error_description", errorDescription.value());
  }

  public OAuthRequestResponse(OAuthRequestStatus status, AuthorizationResponse response) {
    this.status = status;
    this.response = response;
    this.redirectUri = response.redirectUriValue();
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

  public String authorizationRequestId() {
    return authorizationRequest.identifier().value();
  }

  public List<String> scopeList() {
    return authorizationRequest.scope().toStringList();
  }

  public String error() {
    return error;
  }

  public String errorDescription() {
    return errorDescription;
  }

  public String sessionKey() {
    return sessionKey;
  }
}
