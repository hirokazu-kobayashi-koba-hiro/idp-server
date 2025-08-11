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

package org.idp.server.core.openid.token.tokenintrospection.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.platform.date.SystemDateTime;

public class TokenIntrospectionVerifier {

  OAuthToken oAuthToken;

  public TokenIntrospectionVerifier(OAuthToken oAuthToken) {
    this.oAuthToken = oAuthToken;
  }

  public TokenIntrospectionRequestStatus verify() {

    if (!oAuthToken.exists()) {
      return TokenIntrospectionRequestStatus.INVALID_TOKEN;
    }
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpiredAccessToken(now)) {
      return TokenIntrospectionRequestStatus.EXPIRED_TOKEN;
    }

    return TokenIntrospectionRequestStatus.OK;
  }
}
