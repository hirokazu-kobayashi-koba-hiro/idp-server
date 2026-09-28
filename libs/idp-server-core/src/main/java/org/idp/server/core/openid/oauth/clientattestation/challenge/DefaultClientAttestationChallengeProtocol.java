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

package org.idp.server.core.openid.oauth.clientattestation.challenge;

import org.idp.server.core.openid.oauth.clientattestation.challenge.handler.ClientAttestationChallengeHandler;
import org.idp.server.core.openid.oauth.clientattestation.challenge.handler.io.ClientAttestationChallengeResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * The boundary where a failure stops being an exception and becomes a response.
 *
 * <p>Nothing the caller sends reaches the handler, so there is no rejection to distinguish: every
 * failure here is this server's own.
 */
public class DefaultClientAttestationChallengeProtocol
    implements ClientAttestationChallengeProtocol {

  LoggerWrapper log = LoggerWrapper.getLogger(DefaultClientAttestationChallengeProtocol.class);

  ClientAttestationChallengeHandler handler;

  public DefaultClientAttestationChallengeProtocol(ClientAttestationChallengeHandler handler) {
    this.handler = handler;
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  @Override
  public ClientAttestationChallengeResponse issue(Tenant tenant) {
    try {
      return handler.handle(tenant);
    } catch (Exception exception) {
      log.error("Client attestation challenge failed: {}", exception.getMessage(), exception);
      return ClientAttestationChallengeResponse.serverError();
    }
  }
}
