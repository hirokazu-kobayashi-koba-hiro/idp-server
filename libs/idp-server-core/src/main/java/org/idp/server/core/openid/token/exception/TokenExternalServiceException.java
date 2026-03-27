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

package org.idp.server.core.openid.token.exception;

import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;

/**
 * TokenExternalServiceException
 *
 * <p>Thrown when a token request fails due to an external service error (e.g., external IdP
 * introspection endpoint returning 5xx, JWKS fetch failure). Returns HTTP 502 with OAuth error code
 * {@code server_error} per RFC 6749 Section 5.2.
 *
 * <p>This is distinct from {@link TokenBadRequestException} (client error, HTTP 400) and from
 * configuration errors ({@link
 * org.idp.server.core.openid.oauth.configuration.exception.ConfigurationInvalidException}).
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-5.2">RFC 6749 Section 5.2</a>
 */
public class TokenExternalServiceException extends RuntimeException {

  String errorDescription;

  public TokenExternalServiceException(String errorDescription) {
    super(errorDescription);
    this.errorDescription = errorDescription;
  }

  public TokenExternalServiceException(String errorDescription, Throwable throwable) {
    super(errorDescription, throwable);
    this.errorDescription = errorDescription;
  }

  public Error error() {
    return new Error("server_error");
  }

  public ErrorDescription errorDescription() {
    return new ErrorDescription(errorDescription);
  }
}
