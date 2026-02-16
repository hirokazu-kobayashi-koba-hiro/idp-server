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

import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OAuthPushedRequestValidator
 *
 * <p>Validates pushed authorization requests (PAR) at the PAR endpoint.
 *
 * <p>RFC 9126 Section 2.1 defines restrictions specific to pushed authorization requests that do
 * not apply to regular authorization requests.
 */
public class OAuthPushedRequestValidator {

  Tenant tenant;
  OAuthRequestParameters parameters;

  public OAuthPushedRequestValidator(Tenant tenant, OAuthRequestParameters parameters) {
    this.tenant = tenant;
    this.parameters = parameters;
  }

  public void validate() {
    throwExceptionIfContainsRequestUri();
  }

  /**
   * RFC 9126 Section 2.1:
   *
   * <p>"The authorization server MUST reject pushed authorization requests that contain the
   * request_uri request parameter."
   *
   * <p>The request_uri parameter is used at the authorization endpoint to reference a previously
   * pushed request. Including it in the PAR request itself is circular and invalid.
   */
  void throwExceptionIfContainsRequestUri() {
    if (parameters.hasRequestUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "pushed authorization request must not contain request_uri parameter",
          tenant);
    }
  }
}
