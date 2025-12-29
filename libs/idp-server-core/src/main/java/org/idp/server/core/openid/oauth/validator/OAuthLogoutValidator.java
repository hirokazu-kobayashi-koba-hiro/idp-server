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
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OAuthLogoutValidator
 *
 * <p>Validates RP-Initiated Logout request parameters before context creation.
 *
 * <p>Note: idp-server requires id_token_hint parameter for all logout requests. This ensures that
 * the End-User can always be identified without requiring a confirmation screen.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class OAuthLogoutValidator {

  Tenant tenant;
  OAuthLogoutParameters parameters;

  public OAuthLogoutValidator(Tenant tenant, OAuthLogoutParameters parameters) {
    this.tenant = tenant;
    this.parameters = parameters;
  }

  /**
   * Validates logout request parameters.
   *
   * @throws OAuthBadRequestException if validation fails
   */
  public void validate() {
    throwExceptionIfDuplicateValue();
    throwExceptionIfNotContainsIdTokenHint();
    throwExceptionIfInvalidIdTokenHintFormat();
  }

  /**
   * id_token_hint is REQUIRED for idp-server.
   *
   * <p>While the RP-Initiated Logout specification marks id_token_hint as RECOMMENDED, idp-server
   * requires it to ensure the End-User can always be identified. This eliminates the need for a
   * confirmation screen.
   *
   * @throws OAuthBadRequestException if id_token_hint is not provided
   */
  void throwExceptionIfNotContainsIdTokenHint() {
    if (!parameters.hasIdTokenHint()) {
      throw new OAuthBadRequestException(
          "invalid_request", "logout request must contain id_token_hint", tenant);
    }
  }

  /**
   * Request parameters MUST NOT be included more than once.
   *
   * @throws OAuthBadRequestException if duplicate parameters exist
   */
  void throwExceptionIfDuplicateValue() {
    List<String> keys = parameters.multiValueKeys();
    if (!keys.isEmpty()) {
      String keysValue = String.join(" ", keys);
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "logout request must not contain duplicate parameters; keys (%s)", keysValue),
          tenant);
    }
  }

  /**
   * id_token_hint must have a valid JWT or JWE structure.
   *
   * <p>Accepts:
   *
   * <ul>
   *   <li>JWS (signed JWT): header.payload.signature (3 parts)
   *   <li>JWE (encrypted JWT): header.encryptedKey.iv.ciphertext.tag (5 parts)
   *   <li>Unsigned JWT: header.payload (2 parts) - not recommended but valid
   * </ul>
   *
   * @throws OAuthBadRequestException if id_token_hint has invalid format
   */
  void throwExceptionIfInvalidIdTokenHintFormat() {
    String idTokenHint = parameters.idTokenHint().value();
    String[] parts = idTokenHint.split("\\.");
    // Valid formats: 2 parts (unsigned), 3 parts (JWS), 5 parts (JWE)
    if (parts.length != 2 && parts.length != 3 && parts.length != 5) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "id_token_hint must be a valid JWT (header.payload.signature) or JWE (header.encryptedKey.iv.ciphertext.tag) format",
          tenant);
    }
  }
}
