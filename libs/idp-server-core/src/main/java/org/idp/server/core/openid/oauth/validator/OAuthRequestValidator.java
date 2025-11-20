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
import java.util.Map;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * validator
 *
 * <p>If an authorization request fails validation due to a missing, invalid, or mismatching
 * redirection URI, the authorization server SHOULD inform the resource owner of the error and MUST
 * NOT automatically redirect the user-agent to the invalid redirection URI.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1.2.4">3.1.2.4. Invalid
 *     Endpoint</a>
 */
public class OAuthRequestValidator {

  Tenant tenant;
  OAuthRequestParameters oAuthRequestParameters;

  public OAuthRequestValidator(Tenant tenant, OAuthRequestParameters oAuthRequestParameters) {
    this.tenant = tenant;
    this.oAuthRequestParameters = oAuthRequestParameters;
  }

  public void validate() {
    throwExceptionIfNotContainsClientId();
    throwExceptionIfDuplicateValue();
    throwExceptionIfInvalidAuthorizationDetails();
  }

  void throwExceptionIfNotContainsClientId() {
    if (!oAuthRequestParameters.hasClientId()) {
      throw new OAuthBadRequestException(
          "invalid_request", "authorization request must contains client_id", tenant);
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
    List<String> keys = oAuthRequestParameters.multiValueKeys();
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
   * <p>RFC 9396 Section 2 states: "The request parameter authorization_details contains, in JSON
   * notation, an array of objects."
   *
   * <p>RFC 9396 Section 5 states: "The AS MUST abort processing and respond with an error
   * invalid_authorization_details to the client if any of the following are true: - is missing
   * required fields for the authorization details type"
   *
   * <p>This method validates:
   *
   * <ul>
   *   <li>authorization_details MUST be a valid JSON array
   *   <li>authorization_details array MUST NOT be empty
   *   <li>Each element MUST contain required 'type' field
   * </ul>
   *
   * @throws OAuthBadRequestException with error code "invalid_authorization_details"
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-2">RFC 9396 Section 2</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-5">RFC 9396 Section 5</a>
   */
  void throwExceptionIfInvalidAuthorizationDetails() {
    if (!oAuthRequestParameters.hasAuthorizationDetails()) {
      return;
    }

    try {
      String object = oAuthRequestParameters.getValueOrEmpty(OAuthRequestKey.authorization_details);
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(object);
      if (!jsonNodeWrapper.isArray()) {
        throw new OAuthBadRequestException(
            "invalid_authorization_details", "authorization_details is not array.", tenant);
      }
      List<Map<String, Object>> listAsMap = jsonNodeWrapper.toListAsMap();
      if (listAsMap.isEmpty()) {
        throw new OAuthBadRequestException(
            "invalid_authorization_details", "authorization_detail object is unspecified.", tenant);
      }
      listAsMap.forEach(
          map -> {
            if (!map.containsKey("type")) {
              throw new OAuthBadRequestException(
                  "invalid_authorization_details",
                  "type is required. authorization_detail object is missing 'type'.",
                  tenant);
            }
          });
    } catch (Exception e) {
      if (e instanceof OAuthBadRequestException) {
        throw (OAuthBadRequestException) e;
      }
      throw new OAuthBadRequestException(
          "invalid_authorization_details", "authorization_details is invalid.", tenant);
    }
  }
}
