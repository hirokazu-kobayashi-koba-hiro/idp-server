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

import java.util.Map;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;

public class CibaRequestObjectValidator implements CibaRequestValidator {

  Map<String, Object> payload;

  public CibaRequestObjectValidator(Map<String, Object> payload) {
    this.payload = payload;
  }

  public void validate() {
    throwExceptionIfInvalidRequestedExpiry();
    throwExceptionIfInvalidAuthorizationDetails();
  }

  void throwExceptionIfInvalidRequestedExpiry() {

    if (!payload.containsKey(OAuthRequestKey.requested_expiry.name())) {
      return;
    }

    String value = payload.get(OAuthRequestKey.requested_expiry.name()).toString();
    throwExceptionIfInvalidRequestedExpiry(value);
  }

  void throwExceptionIfInvalidAuthorizationDetails() {
    if (!payload.containsKey(OAuthRequestKey.authorization_details.name())) {
      return;
    }

    Object object = payload.get(OAuthRequestKey.authorization_details.name());
    throwExceptionIfInvalidAuthorizationDetails(object);
  }
}
