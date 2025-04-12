package org.idp.server.core.discovery;

import java.util.Map;
import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.basic.dependency.protcol.DefaultAuthorizationProvider;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.discovery.handler.DiscoveryHandler;
import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.discovery.handler.io.JwksRequestStatus;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestStatus;
import org.idp.server.core.tenant.TenantIdentifier;

public class DefaultDiscoveryProtocol implements DiscoveryProtocol {

  DiscoveryHandler discoveryHandler;

  public DefaultDiscoveryProtocol(ServerConfigurationRepository serverConfigurationRepository) {
    this.discoveryHandler = new DiscoveryHandler(serverConfigurationRepository);
  }

  @Override
  public AuthorizationProtocolProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  public ServerConfigurationRequestResponse getConfiguration(TenantIdentifier tenantIdentifier) {
    try {
      return discoveryHandler.getConfiguration(tenantIdentifier);
    } catch (Exception exception) {
      return new ServerConfigurationRequestResponse(
          ServerConfigurationRequestStatus.SERVER_ERROR, Map.of());
    }
  }

  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {
    try {
      return discoveryHandler.getJwks(tenantIdentifier);
    } catch (Exception exception) {
      return new JwksRequestResponse(JwksRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
