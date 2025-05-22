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


package org.idp.server.core.oidc.discovery;

import java.util.Map;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.discovery.handler.DiscoveryHandler;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestStatus;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestStatus;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DefaultDiscoveryProtocol implements DiscoveryProtocol {

  DiscoveryHandler discoveryHandler;

  public DefaultDiscoveryProtocol(
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository) {
    this.discoveryHandler = new DiscoveryHandler(authorizationServerConfigurationQueryRepository);
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  public ServerConfigurationRequestResponse getConfiguration(Tenant tenant) {
    try {
      return discoveryHandler.getConfiguration(tenant);
    } catch (Exception exception) {
      return new ServerConfigurationRequestResponse(
          ServerConfigurationRequestStatus.SERVER_ERROR, Map.of());
    }
  }

  public JwksRequestResponse getJwks(Tenant tenant) {
    try {
      return discoveryHandler.getJwks(tenant);
    } catch (Exception exception) {
      return new JwksRequestResponse(JwksRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
