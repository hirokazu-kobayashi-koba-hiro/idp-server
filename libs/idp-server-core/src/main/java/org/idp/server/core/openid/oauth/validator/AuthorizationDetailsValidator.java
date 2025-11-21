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
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * RFC 9396 Section 2 & Section 5 - Authorization Details Validation
 *
 * <p>Common validation logic for authorization_details parameter that can be used by both normal
 * request validators and request object validators.
 *
 * <p>RFC 9396 Section 2 states: "The request parameter authorization_details contains, in JSON
 * notation, an array of objects."
 *
 * <p>RFC 9396 Section 5 states: "The AS MUST abort processing and respond with an error
 * invalid_authorization_details to the client if any of the following are true: - is missing
 * required fields for the authorization details type"
 *
 * <p>This class validates:
 *
 * <ul>
 *   <li>authorization_details MUST be a valid JSON array
 *   <li>authorization_details array MUST NOT be empty
 *   <li>Each element MUST contain required 'type' field
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-2">RFC 9396 Section 2</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-5">RFC 9396 Section 5</a>
 */
public class AuthorizationDetailsValidator {

  /**
   * Validate authorization_details from String (for normal request parameters)
   *
   * @param object authorization_details value as String
   * @param tenant Tenant for error context
   * @throws OAuthBadRequestException with error code "invalid_authorization_details"
   */
  public static void validate(String object, Tenant tenant) {
    try {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(object);
      validate(jsonNodeWrapper, tenant);
    } catch (Exception e) {
      if (e instanceof OAuthBadRequestException) {
        throw (OAuthBadRequestException) e;
      }
      throw new OAuthBadRequestException(
          "invalid_authorization_details", "authorization_details is invalid.", tenant);
    }
  }

  /**
   * Validate authorization_details from Object (for request object JWT payload)
   *
   * @param object authorization_details value as Object
   * @param tenant Tenant for error context
   * @throws OAuthBadRequestException with error code "invalid_authorization_details"
   */
  public static void validate(Object object, Tenant tenant) {
    try {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(object);
      validate(jsonNodeWrapper, tenant);
    } catch (Exception e) {
      if (e instanceof OAuthBadRequestException) {
        throw (OAuthBadRequestException) e;
      }
      throw new OAuthBadRequestException(
          "invalid_authorization_details", "authorization_details is invalid.", tenant);
    }
  }

  /**
   * Core validation logic for authorization_details
   *
   * @param jsonNodeWrapper Parsed JSON representation
   * @param tenant Tenant for error context
   * @throws OAuthBadRequestException with error code "invalid_authorization_details"
   */
  private static void validate(JsonNodeWrapper jsonNodeWrapper, Tenant tenant) {
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
  }
}
