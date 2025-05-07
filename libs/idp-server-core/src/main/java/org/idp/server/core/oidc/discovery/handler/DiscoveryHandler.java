package org.idp.server.core.oidc.discovery.handler;

import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.discovery.JwksResponseCreator;
import org.idp.server.core.oidc.discovery.ServerConfigurationResponseCreator;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestStatus;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestStatus;

public class DiscoveryHandler {

  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;

  public DiscoveryHandler(
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository) {
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
  }

  public ServerConfigurationRequestResponse getConfiguration(Tenant tenant) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationRepository.get(tenant);

    ServerConfigurationResponseCreator serverConfigurationResponseCreator =
        new ServerConfigurationResponseCreator(authorizationServerConfiguration);
    Map<String, Object> content = serverConfigurationResponseCreator.create();

    return new ServerConfigurationRequestResponse(ServerConfigurationRequestStatus.OK, content);
  }

  public JwksRequestResponse getJwks(Tenant tenant) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationRepository.get(tenant);

    JwksResponseCreator jwksResponseCreator =
        new JwksResponseCreator(authorizationServerConfiguration);
    Map<String, Object> content = jwksResponseCreator.create();

    return new JwksRequestResponse(JwksRequestStatus.OK, content);
  }
}
