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

package org.idp.server.core.extension.ciba.validator;

import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.extension.ciba.handler.io.CibaRequest;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;

public class CibaNormalRequestValidator implements CibaRequestValidator {

  CibaRequest request;

  public CibaNormalRequestValidator(CibaRequest request) {
    this.request = request;
  }

  /**
   * Validates the CIBA request parameters.
   *
   * <p>Per RFC 7521 Section 4.2, when using JWT-based client authentication, the client_id can be
   * identified from:
   *
   * <ol>
   *   <li>Explicit client_id parameter in request body
   *   <li>HTTP Basic Authentication header
   *   <li>Issuer (iss) claim from client_assertion JWT
   * </ol>
   *
   * @throws BackchannelAuthenticationBadRequestException if client cannot be identified
   */
  public void validate() {
    if (!request.hasClientId()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "Unable to identify client. Provide client_id parameter, HTTP Basic Authentication, or client_assertion with iss claim.");
    }
    throwExceptionIfInvalidRequestedExpiry();
    throwExceptionIfInvalidAuthorizationDetails();
  }

  void throwExceptionIfInvalidRequestedExpiry() {
    CibaRequestParameters parameters = request.toParameters();
    if (!parameters.hasRequestedExpiry()) {
      return;
    }

    String value = parameters.getValueOrEmpty(OAuthRequestKey.requested_expiry);
    throwExceptionIfInvalidRequestedExpiry(value);
  }

  void throwExceptionIfInvalidAuthorizationDetails() {
    CibaRequestParameters parameters = request.toParameters();
    if (!parameters.hasAuthorizationDetails()) {
      return;
    }

    String object = parameters.getValueOrEmpty(OAuthRequestKey.authorization_details);
    throwExceptionIfInvalidAuthorizationDetails(object);
  }
}
