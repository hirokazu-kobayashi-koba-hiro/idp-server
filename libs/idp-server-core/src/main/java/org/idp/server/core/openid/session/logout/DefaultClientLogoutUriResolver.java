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

package org.idp.server.core.openid.session.logout;

import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DefaultClientLogoutUriResolver implements ClientLogoutUriResolver {

  private final Tenant tenant;
  private final ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public DefaultClientLogoutUriResolver(
      Tenant tenant, ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.tenant = tenant;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  @Override
  public String resolveFrontChannelLogoutUri(String clientId) {
    ClientConfiguration config = getClientConfiguration(clientId);
    if (config == null || !config.exists()) {
      return null;
    }
    return config.frontchannelLogoutUri();
  }

  @Override
  public String resolveBackChannelLogoutUri(String clientId) {
    ClientConfiguration config = getClientConfiguration(clientId);
    if (config == null || !config.exists()) {
      return null;
    }
    return config.backchannelLogoutUri();
  }

  @Override
  public boolean isFrontChannelLogoutSessionRequired(String clientId) {
    ClientConfiguration config = getClientConfiguration(clientId);
    if (config == null || !config.exists()) {
      return false;
    }
    return config.frontchannelLogoutSessionRequired();
  }

  @Override
  public boolean isBackChannelLogoutSessionRequired(String clientId) {
    ClientConfiguration config = getClientConfiguration(clientId);
    if (config == null || !config.exists()) {
      return false;
    }
    return config.backchannelLogoutSessionRequired();
  }

  private ClientConfiguration getClientConfiguration(String clientId) {
    try {
      return clientConfigurationQueryRepository.get(tenant, new RequestedClientId(clientId));
    } catch (Exception e) {
      return null;
    }
  }
}
