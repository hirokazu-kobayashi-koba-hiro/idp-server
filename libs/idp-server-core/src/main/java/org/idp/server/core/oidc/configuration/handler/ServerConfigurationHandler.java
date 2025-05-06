package org.idp.server.core.oidc.configuration.handler;

import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;

public class ServerConfigurationHandler {

  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  JsonConverter jsonConverter;

  public ServerConfigurationHandler(
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository) {
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  // TODO
  public AuthorizationServerConfiguration handleRegistration(Tenant tenant, String json) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(json, AuthorizationServerConfiguration.class);
    authorizationServerConfiguration.setTenantId(UUID.randomUUID().toString());
    authorizationServerConfigurationRepository.register(tenant, authorizationServerConfiguration);

    return authorizationServerConfiguration;
  }
}
