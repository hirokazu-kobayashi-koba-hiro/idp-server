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

package org.idp.server.core.openid.clientinstance.registration;

import org.idp.server.core.openid.clientinstance.registration.handler.ClientInstanceRegistrationErrorHandler;
import org.idp.server.core.openid.clientinstance.registration.handler.ClientInstanceRegistrationHandler;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceChallengeRequest;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceRegisterRequest;
import org.idp.server.core.openid.clientinstance.registration.handler.io.ClientInstanceRegistrationResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * The boundary where a registration failure stops being an exception and becomes a response.
 *
 * <p>Nothing above this needs to know how the flow can fail: the handler is free to throw, and what
 * leaves here is always a response the caller may see.
 */
public class DefaultClientInstanceRegistrationProtocol
    implements ClientInstanceRegistrationProtocol {

  ClientInstanceRegistrationHandler handler;
  ClientInstanceRegistrationErrorHandler errorHandler;

  public DefaultClientInstanceRegistrationProtocol(ClientInstanceRegistrationHandler handler) {
    this.handler = handler;
    this.errorHandler = new ClientInstanceRegistrationErrorHandler();
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  @Override
  public ClientInstanceRegistrationResponse challenge(
      Tenant tenant, ClientInstanceChallengeRequest request) {
    try {
      return handler.handleChallenge(tenant, request);
    } catch (Exception exception) {
      return errorHandler.handle("registration challenge", exception);
    }
  }

  @Override
  public ClientInstanceRegistrationResponse register(
      Tenant tenant, ClientInstanceRegisterRequest request) {
    try {
      return handler.handleRegister(tenant, request);
    } catch (Exception exception) {
      return errorHandler.handle("registration", exception);
    }
  }
}
