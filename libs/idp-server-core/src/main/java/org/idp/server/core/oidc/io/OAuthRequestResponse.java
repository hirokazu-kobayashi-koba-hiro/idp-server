/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.idp.server.core.oidc.io;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.response.AuthorizationErrorResponse;
import org.idp.server.core.oidc.response.AuthorizationResponse;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestStatus status;
  AuthorizationRequest authorizationRequest;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;
  AuthorizationResponse response;
  OAuthSession session;
  AuthorizationErrorResponse errorResponse;
  Map<String, String> contents;
  String frontUrl;
  String redirectUri;
  String error;
  String errorDescription;
  String sessionKey;

  public OAuthRequestResponse() {}

  public OAuthRequestResponse(
      OAuthRequestStatus status,
      OAuthRequestContext context,
      OAuthSession session,
      String frontUrl) {
    this.status = status;
    this.authorizationRequest = context.authorizationRequest();
    this.authorizationServerConfiguration = context.serverConfiguration();
    this.clientConfiguration = context.clientConfiguration();
    this.session = session;
    // FIXME bad code
    this.contents = Map.of("id", context.identifier().value());
    this.sessionKey = context.sessionKeyValue();
    this.frontUrl = frontUrl;
  }

  public OAuthRequestResponse(
      OAuthRequestStatus status, String frontUrl, Error error, ErrorDescription errorDescription) {
    this.status = status;
    this.frontUrl = frontUrl;
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

  public AuthorizationServerConfiguration serverConfiguration() {
    return authorizationServerConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public Map<String, String> contents() {
    return contents;
  }

  public String frontUrl() {
    return frontUrl;
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

  public int oauthAuthorizationRequestExpiresIn() {
    return authorizationServerConfiguration.oauthAuthorizationRequestExpiresIn();
  }

  public boolean isOK() {
    return status.isSuccess();
  }

  public List<AuthenticationPolicy> authenticationPolicies() {
    return authorizationServerConfiguration.authenticationPolicies();
  }

  // FIXME bad code
  public AuthenticationPolicy findSatisfiedAuthenticationPolicy() {
    List<AuthenticationPolicy> authenticationPolicies =
        authorizationServerConfiguration.authenticationPolicies();
    return authenticationPolicies.stream()
        .filter(
            authenticationPolicy ->
                authenticationPolicy.anyMatch(
                    AuthorizationFlow.OAUTH,
                    authorizationRequest.acrValues(),
                    authorizationRequest.scopes()))
        .max(Comparator.comparingInt(AuthenticationPolicy::priority))
        .orElse(new AuthenticationPolicy());
  }
}
