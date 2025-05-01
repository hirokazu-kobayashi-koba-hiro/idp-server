package org.idp.server.core.oidc.discovery;

import java.util.Map;
import org.idp.server.basic.dependency.protocol.AuthorizationProtocolProvider;
import org.idp.server.basic.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.oidc.discovery.handler.DiscoveryHandler;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestStatus;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestStatus;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class DefaultDiscoveryProtocol implements DiscoveryProtocol {

  DiscoveryHandler discoveryHandler;

  public DefaultDiscoveryProtocol(ServerConfigurationRepository serverConfigurationRepository) {
    this.discoveryHandler = new DiscoveryHandler(serverConfigurationRepository);
  }

  @Override
  public AuthorizationProtocolProvider authorizationProtocolProvider() {
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
