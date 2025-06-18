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

import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.extension.ciba.handler.io.CibaRequest;
import org.idp.server.platform.json.JsonNodeWrapper;

public class CibaRequestValidator {

  CibaRequest request;

  public CibaRequestValidator(CibaRequest request) {
    this.request = request;
  }

  public void validate() {
    if (!request.hasClientId()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "client_id is in neither body or header. client_id is required");
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

    if (!value.matches("^[1-9][0-9]*$")) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "requested_expiry must be a positive integer string.");
    }

    try {
      Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "requested_expiry is out of range for integer.");
    }
  }

  void throwExceptionIfInvalidAuthorizationDetails() {
    CibaRequestParameters parameters = request.toParameters();
    if (!parameters.hasAuthorizationDetails()) {
      return;
    }

    try {
      String object = parameters.getValueOrEmpty(OAuthRequestKey.authorization_details);
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(object);
      if (!jsonNodeWrapper.isArray()) {
        throw new BackchannelAuthenticationBadRequestException(
            "invalid_request", "authorization_details is not array.");
      }
      List<Map<String, Object>> listAsMap = jsonNodeWrapper.toListAsMap();
      if (listAsMap.isEmpty()) {
        throw new BackchannelAuthenticationBadRequestException(
            "invalid_request", "authorization_detail object is unspecified.");
      }
      listAsMap.forEach(
          map -> {
            if (!map.containsKey("type")) {
              throw new BackchannelAuthenticationBadRequestException(
                  "invalid_request",
                  "type is required. authorization_detail object is missing 'type'.");
            }
          });
    } catch (Exception e) {
      if (e instanceof BackchannelAuthenticationBadRequestException) {
        throw (BackchannelAuthenticationBadRequestException) e;
      }
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "authorization_details is invalid.");
    }
  }
}
