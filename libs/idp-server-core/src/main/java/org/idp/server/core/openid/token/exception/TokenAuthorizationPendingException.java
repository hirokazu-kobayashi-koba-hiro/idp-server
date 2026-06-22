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

/**
 * Represents the CIBA {@code authorization_pending} token error response.
 *
 * <p>{@code authorization_pending} is a normal intermediate response defined in <a
 * href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.11">CIBA
 * Core, Section 11</a>: it is returned repeatedly while a polling-mode client waits for the
 * end-user to be authenticated. It is therefore expected traffic rather than a fault, so {@link
 * org.idp.server.core.openid.token.handler.token.TokenRequestErrorHandler} logs it at INFO instead
 * of WARN.
 *
 * <p>Modeled as a subclass of {@link TokenBadRequestException} so it keeps the same 400 Bad Request
 * response mapping while the error handler can branch on the concrete type to lower the log level.
 * The dedicated branch must be evaluated before the {@link TokenBadRequestException} branch so the
 * subclass matches first.
 */
public class TokenAuthorizationPendingException extends TokenBadRequestException {

  public TokenAuthorizationPendingException(String errorDescription) {
    super("authorization_pending", errorDescription);
  }
}
