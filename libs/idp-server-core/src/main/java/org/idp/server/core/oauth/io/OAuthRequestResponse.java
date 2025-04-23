package org.idp.server.core.oauth.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;

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

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequest.identifier();
  }

  public List<String> scopeList() {
    return authorizationRequest.scopes().toStringList();
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

  public List<String> availableAuthenticationTypes() {
    return serverConfiguration.availableAuthenticationMethods();
  }

  public List<String> requiredAnyOfAuthenticationTypes() {
    List<String> methods = new ArrayList<>();
    if (authorizationRequest.isFapiProfile()) {
      methods.add("webauthn");
      methods.add("fido-uaf");
      methods.add("fido2");
    }
    return methods;
  }

  public int oauthAuthorizationRequestExpiresIn() {
    return serverConfiguration.oauthAuthorizationRequestExpiresIn();
  }

  public boolean isOK() {
    return status.isSuccess();
  }
}
