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

package org.idp.server.core.openid.oauth.handler;

import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.io.OAuthLogoutResponse;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * OAuthLogoutErrorHandler
 *
 * <p>Handles exceptions during RP-Initiated Logout processing.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class OAuthLogoutErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthLogoutErrorHandler.class);

  public OAuthLogoutResponse handle(Exception exception) {
    if (exception instanceof OAuthBadRequestException badRequestException) {
      log.warn(badRequestException.getMessage());
      return OAuthLogoutResponse.badRequest(
          badRequestException.error().value(), badRequestException.errorDescription().value());
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn("not found client configuration");
      log.warn(exception.getMessage());
      return OAuthLogoutResponse.badRequest("invalid_request", exception.getMessage());
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn("not found server configuration");
      log.warn(exception.getMessage());
      return OAuthLogoutResponse.badRequest("invalid_request", exception.getMessage());
    }
    log.error(exception.getMessage(), exception);
    return OAuthLogoutResponse.serverError(exception.getMessage());
  }
}
