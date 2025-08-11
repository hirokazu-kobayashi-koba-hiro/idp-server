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

package org.idp.server.core.extension.ciba.handler.io;

import java.util.Comparator;
import java.util.List;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationErrorResponse;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.oauth.type.ContentType;
import org.idp.server.core.openid.oauth.type.ciba.AuthReqId;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;

// TODO to be more readable name
public class CibaIssueResponse {
  CibaRequestStatus status;
  CibaRequestContext cibaRequestContext;
  BackchannelAuthenticationRequest request;
  BackchannelAuthenticationResponse response;
  User user;
  BackchannelAuthenticationErrorResponse errorResponse;
  ContentType contentType;

  public CibaIssueResponse(
      CibaRequestStatus status,
      CibaRequestContext cibaRequestContext,
      BackchannelAuthenticationResponse response,
      User user) {
    this.status = status;
    this.cibaRequestContext = cibaRequestContext;
    this.request = cibaRequestContext.backchannelAuthenticationRequest();
    this.response = response;
    this.user = user;
    this.errorResponse = new BackchannelAuthenticationErrorResponse();
    this.contentType = ContentType.application_json;
  }

  public CibaIssueResponse(
      CibaRequestStatus cibaRequestStatus,
      BackchannelAuthenticationErrorResponse backchannelAuthenticationErrorResponse) {
    this.status = cibaRequestStatus;
    this.errorResponse = backchannelAuthenticationErrorResponse;
    this.contentType = ContentType.application_json;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public BackchannelAuthenticationRequest request() {
    return request;
  }

  public BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier() {
    return request.identifier();
  }

  public BindingMessage bindingMessage() {
    return request.bindingMessage();
  }

  public BackchannelAuthenticationResponse response() {
    return response;
  }

  public User user() {
    return user;
  }

  public ExpiresIn expiresIn() {
    return cibaRequestContext.expiresIn();
  }

  public BackchannelAuthenticationErrorResponse errorResponse() {
    return errorResponse;
  }

  public ContentType contentType() {
    return contentType;
  }

  public String contentTypeValue() {
    return contentType.value();
  }

  public String contents() {
    if (status.isOK()) {
      return response.contents();
    }
    return errorResponse.contents();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public CibaRequestResponse toResponse() {
    return new CibaRequestResponse(status, response);
  }

  public CibaRequestResponse toErrorResponse() {
    return new CibaRequestResponse(status, errorResponse);
  }

  public AcrValues acrValues() {
    return cibaRequestContext.acrValues();
  }

  public Scopes scopes() {
    return cibaRequestContext.scopes();
  }

  public AuthenticationInteractionType defaultCibaAuthenticationInteractionType() {
    return cibaRequestContext.defaultCibaAuthenticationInteractionType();
  }

  public ClientAttributes clientAttributes() {
    return cibaRequestContext.clientAttributes();
  }

  public AuthenticationPolicy findSatisfiedAuthenticationPolicy() {
    List<AuthenticationPolicy> authenticationPolicies = cibaRequestContext.authenticationPolicies();
    return authenticationPolicies.stream()
        .filter(policy -> policy.anyMatch(AuthFlow.CIBA, request.acrValues(), request.scopes()))
        .min(Comparator.comparingInt(AuthenticationPolicy::priority))
        .orElse(new AuthenticationPolicy());
  }

  public AuthReqId authReqId() {
    return response.authReqId();
  }
}
