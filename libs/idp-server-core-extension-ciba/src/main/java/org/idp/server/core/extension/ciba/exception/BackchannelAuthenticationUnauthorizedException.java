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

package org.idp.server.core.extension.ciba.exception;

import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.platform.exception.UnauthorizedException;

/**
 * Exception for backchannel authentication endpoint errors that should return HTTP 401.
 *
 * <p>According to OpenID Connect CIBA Core Section 13, the following errors should return HTTP 401
 * Unauthorized:
 *
 * <ul>
 *   <li>invalid_client - Client authentication failed (e.g., invalid client credentials, unknown
 *       client, no client authentication included, or unsupported authentication method)
 * </ul>
 *
 * @see <a
 *     href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.13">CIBA
 *     Core Section 13</a>
 */
public class BackchannelAuthenticationUnauthorizedException extends UnauthorizedException {
  String error;
  String errorDescription;

  public BackchannelAuthenticationUnauthorizedException(String error, String errorDescription) {
    super(errorDescription);
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return new Error(error);
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }
}
