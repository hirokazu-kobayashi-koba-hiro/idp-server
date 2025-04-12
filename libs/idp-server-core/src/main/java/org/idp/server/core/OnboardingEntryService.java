package org.idp.server.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.admin.OnboardingApi;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRegistrator;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.organization.*;
import org.idp.server.core.tenant.*;

@Transactional
public class OnboardingEntryService implements OnboardingApi {

  TenantRepository tenantRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  ServerConfigurationRepository serverConfigurationRepository;
  JsonConverter jsonConverter;

  public OnboardingEntryService(
      TenantRepository tenantRepository,
      OrganizationRepository organizationRepository,
      UserRepository userRepository,
      ServerConfigurationRepository serverConfigurationRepository) {
    this.tenantRepository = tenantRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userRepository);
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO improve logic
  public Map<String, Object> initialize(User operator, Map<String, Object> request) {

    String organizationName = (String) request.getOrDefault("organization_name", "");
    String tenantName = (String) request.getOrDefault("tenant_name", "");
    String serverDomain = (String) request.getOrDefault("server_domain", "");
    String serverConfig = (String) request.get("server_configuration");
    TenantIdentifier tenantIdentifier = new TenantIdentifier(UUID.randomUUID().toString());
    TenantDomain tenantDomain = new TenantDomain(serverDomain + "/" + tenantIdentifier.value());
    String replacedConfig =
        serverConfig
            .replace("ISSUER", tenantDomain.value())
            .replace("TENANT_ID", tenantIdentifier.value());

    ServerConfiguration serverConfiguration =
        jsonConverter.read(replacedConfig, ServerConfiguration.class);

    Organization organization =
        new Organization(
            new OrganizationIdentifier(UUID.randomUUID().toString()),
            new OrganizationName(organizationName),
            new OrganizationDescription(""));

    Tenant tenant =
        new Tenant(tenantIdentifier, new TenantName(tenantName), TenantType.PUBLIC, tenantDomain);
    organization.assign(tenant);

    tenantRepository.register(tenant);
    serverConfigurationRepository.register(serverConfiguration);
    organizationRepository.register(organization);

    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", organization.toMap());
    operator.setCustomProperties(newCustomProperties);
    userRegistrator.registerOrUpdate(tenantRepository.getAdmin(), operator);

    return organization.toMap();
  }
}
