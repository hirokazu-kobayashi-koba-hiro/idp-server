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

package org.idp.server.core.oidc.discovery.handler;

import java.util.Map;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.discovery.JwksResponseCreator;
import org.idp.server.core.oidc.discovery.ServerConfigurationResponseCreator;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestStatus;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DiscoveryHandler {

  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;

  public DiscoveryHandler(
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository) {
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
  }

  public ServerConfigurationRequestResponse getConfiguration(Tenant tenant) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    ServerConfigurationResponseCreator serverConfigurationResponseCreator =
        new ServerConfigurationResponseCreator(authorizationServerConfiguration);
    Map<String, Object> content = serverConfigurationResponseCreator.create();

    return new ServerConfigurationRequestResponse(ServerConfigurationRequestStatus.OK, content);
  }

  public JwksRequestResponse getJwks(Tenant tenant) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    JwksResponseCreator jwksResponseCreator =
        new JwksResponseCreator(authorizationServerConfiguration);
    Map<String, Object> content = jwksResponseCreator.create();

    return new JwksRequestResponse(JwksRequestStatus.OK, content);
  }
}
