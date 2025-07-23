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

package org.idp.server.core.extension.identity.verification.configuration.common;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.http.HmacAuthenticationConfig;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class IdentityVerificationCommonConfiguration implements JsonReadable {
  String callbackApplicationIdParam;
  String authType;
  OAuthAuthorizationConfiguration oauthAuthorization = new OAuthAuthorizationConfiguration();
  HmacAuthenticationConfig hmacAuthentication = new HmacAuthenticationConfig();

  public IdentityVerificationCommonConfiguration() {}

  public String callbackApplicationIdParam() {
    return callbackApplicationIdParam;
  }

  public boolean hasCallbackApplicationIdParam() {
    return callbackApplicationIdParam != null && !callbackApplicationIdParam.isEmpty();
  }

  public String authType() {
    return authType;
  }

  public boolean hasAuthType() {
    return authType != null && !authType.isEmpty();
  }

  public boolean hasOAuthAuthorization() {
    return oauthAuthorization != null && oauthAuthorization.exists();
  }

  public OAuthAuthorizationConfiguration oAuthAuthorization() {
    return oauthAuthorization;
  }

  public boolean hasHmacAuthentication() {
    return hmacAuthentication != null && hmacAuthentication.exists();
  }

  public HmacAuthenticationConfig hmacAuthentication() {
    return hmacAuthentication;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasCallbackApplicationIdParam())
      map.put("callback_application_id_param", callbackApplicationIdParam);
    if (hasAuthType()) map.put("authType", authType);
    if (hasOAuthAuthorization()) map.put("oauth_authorization", oauthAuthorization.toMap());
    if (hasHmacAuthentication()) map.put("hmac_authentication", hmacAuthentication.toMap());
    return map;
  }
}
