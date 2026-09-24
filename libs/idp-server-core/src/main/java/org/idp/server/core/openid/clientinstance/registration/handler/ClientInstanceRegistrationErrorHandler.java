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

package org.idp.server.core.openid.clientinstance.registration.handler;

import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationException;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceRegistrationResponse;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Maps a registration failure to a response.
 *
 * <p>Every rejection answers the same way. The challenge endpoint is unauthenticated, so a
 * distinguishable reason would let a caller probe which clients take part in registration, or which
 * check a forged registration got past — see {@link
 * ClientInstanceRegistrationResponse#invalidRequest()}. An unknown client_id is therefore reported
 * as a bad request rather than as not-found.
 *
 * <p>What the caller cannot see, the log does: a rejection carries its reason at warn, and anything
 * unrecognised is a server fault, logged at error with its stack rather than disguised as the
 * caller's mistake.
 */
public class ClientInstanceRegistrationErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(ClientInstanceRegistrationErrorHandler.class);

  public ClientInstanceRegistrationResponse handle(String operation, Exception exception) {

    if (exception instanceof ClientInstanceRegistrationException
        || exception instanceof PlatformAttestationVerificationException
        || exception instanceof ClientConfigurationNotFoundException) {
      log.warn("Client instance {} rejected: {}", operation, exception.getMessage());
      return ClientInstanceRegistrationResponse.invalidRequest(exception.getMessage());
    }

    log.error("Client instance {} failed: {}", operation, exception.getMessage(), exception);
    return ClientInstanceRegistrationResponse.serverError(exception.getMessage());
  }
}
