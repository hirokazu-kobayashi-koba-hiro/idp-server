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

package org.idp.server.core.openid.oauth.response;

import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.type.extension.ResponseModeValue;
import org.idp.server.core.openid.oauth.type.oauth.ResponseType;
import org.idp.server.core.openid.oauth.type.oidc.ResponseMode;

public interface ResponseModeDecidable {

  default ResponseModeValue decideResponseModeValue(
      ResponseType responseType, ResponseMode responseMode) {

    if (responseMode.isDefinedResponseModeValue()) {

      return new ResponseModeValue(responseMode.responseModeValue());
    } else if (responseType.isAuthorizationCodeFlow()
        || responseType.isUndefined()
        || responseType.isUnknown()) {

      return ResponseModeValue.query();
    } else {

      return ResponseModeValue.fragment();
    }
  }

  default boolean isJwtMode(
      AuthorizationProfile profile, ResponseType responseType, ResponseMode responseMode) {
    if (responseMode.isJwtMode()) {
      return true;
    }
    if (profile.isFapiAdvance() && !responseType.isCodeIdToken()) {
      return true;
    }
    return false;
  }
}
