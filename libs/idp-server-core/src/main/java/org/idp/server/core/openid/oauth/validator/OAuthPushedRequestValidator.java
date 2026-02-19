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

package org.idp.server.core.openid.oauth.validator;

import java.util.List;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.io.OAuthPushedRequest;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OAuthPushedRequestValidator
 *
 * <p>Validates pushed authorization requests (PAR) at the PAR endpoint.
 *
 * <p>Per RFC 7521 Section 4.2, client_id may be resolved from client_assertion JWT's iss claim, so
 * this validator uses {@link OAuthPushedRequest} which provides client_id resolution with fallback
 * (explicit parameter → Basic Auth → client_assertion JWT iss).
 *
 * <p>This follows the same pattern as {@code CibaNormalRequestValidator} which takes {@code
 * CibaRequest} with client_id fallback.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9126#section-2.1">RFC 9126 Section 2.1</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7521#section-4.2">RFC 7521 Section 4.2</a>
 */
public class OAuthPushedRequestValidator {

  Tenant tenant;
  OAuthPushedRequest request;

  public OAuthPushedRequestValidator(Tenant tenant, OAuthPushedRequest request) {
    this.tenant = tenant;
    this.request = request;
  }

  public void validate() {
    throwExceptionIfNotContainsClientId();
    throwExceptionIfDuplicateValue();
    throwExceptionIfInvalidAuthorizationDetails();
    throwExceptionIfContainsRequestUri();
  }

  /**
   * Validates that the client can be identified.
   *
   * <p>Per RFC 7521 Section 4.2, client_id is resolved with fallback:
   *
   * <ol>
   *   <li>Explicit client_id parameter in request body
   *   <li>HTTP Basic Authentication header (username)
   *   <li>Issuer (iss) claim from client_assertion JWT (RFC 7523)
   * </ol>
   */
  void throwExceptionIfNotContainsClientId() {
    if (!request.hasClientId()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "Unable to identify client. Provide client_id parameter, HTTP Basic Authentication, or client_assertion with iss claim.",
          tenant);
    }
  }

  /**
   * 3.1. Authorization Endpoint validation
   *
   * <p>Request and response parameters MUST NOT be included more than once.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1">3.1. Authorization
   *     Endpoint</a>
   */
  void throwExceptionIfDuplicateValue() {
    OAuthRequestParameters parameters = request.toOAuthRequestParameters();
    List<String> keys = parameters.multiValueKeys();
    List<String> filteredKeys = keys.stream().filter(key -> !key.equals("resource")).toList();
    if (!filteredKeys.isEmpty()) {
      String keysValue = String.join(" ", filteredKeys);
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "authorization request must not contains duplicate value; keys (%s)", keysValue),
          tenant);
    }
  }

  /**
   * RFC 9396 Section 2 & Section 5 - Authorization Details Validation
   *
   * @see AuthorizationDetailsValidator
   */
  void throwExceptionIfInvalidAuthorizationDetails() {
    OAuthRequestParameters parameters = request.toOAuthRequestParameters();
    if (!parameters.hasAuthorizationDetails()) {
      return;
    }

    String object = parameters.getValueOrEmpty(OAuthRequestKey.authorization_details);
    AuthorizationDetailsValidator.validate(object, tenant);
  }

  /**
   * RFC 9126 Section 2.1:
   *
   * <p>"The authorization server MUST reject pushed authorization requests that contain the
   * request_uri request parameter."
   */
  void throwExceptionIfContainsRequestUri() {
    OAuthRequestParameters parameters = request.toOAuthRequestParameters();
    if (parameters.hasRequestUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "pushed authorization request must not contain request_uri parameter",
          tenant);
    }
  }
}
