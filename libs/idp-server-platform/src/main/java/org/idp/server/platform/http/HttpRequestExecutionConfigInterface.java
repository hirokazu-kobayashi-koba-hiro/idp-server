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

package org.idp.server.platform.http;

import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public interface HttpRequestExecutionConfigInterface {

  HttpRequestUrl httpRequestUrl();

  HttpMethod httpMethod();

  HttpRequestAuthType httpRequestAuthType();

  boolean hasOAuthAuthorization();

  OAuthAuthorizationConfiguration oauthAuthorization();

  boolean hasHmacAuthentication();

  HmacAuthenticationConfig hmacAuthentication();

  HttpRequestMappingRules pathMappingRules();

  HttpRequestMappingRules headerMappingRules();

  HttpRequestMappingRules bodyMappingRules();

  HttpRequestMappingRules queryMappingRules();

  default boolean hasRetryConfiguration() {
    return false;
  }

  default HttpRetryConfiguration retryConfiguration() {
    return HttpRetryConfiguration.noRetry();
  }

  default boolean hasRequestTimeout() {
    return false;
  }

  default int requestTimeoutSeconds() {
    return 30;
  }

  default boolean hasResponseConfigs() {
    return false;
  }

  default HttpResponseResolveConfigs responseResolveConfigs() {
    return new HttpResponseResolveConfigs();
  }
}
