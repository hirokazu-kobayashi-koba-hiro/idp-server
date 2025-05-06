package org.idp.server.usecases;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.admin.OnboardingApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRegistrator;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.organization.*;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;

@Transaction
public class OnboardingEntryService implements OnboardingApi {

  TenantRepository tenantRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  ServerConfigurationRepository serverConfigurationRepository;
  JsonConverter jsonConverter;

  public OnboardingEntryService(TenantRepository tenantRepository, OrganizationRepository organizationRepository, UserQueryRepository userQueryRepository, ServerConfigurationRepository serverConfigurationRepository) {
    this.tenantRepository = tenantRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository);
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  // TODO improve logic
  public Map<String, Object> initialize(TenantIdentifier adminTenantIdentifier, User operator, Map<String, Object> request) {

    String organizationName = (String) request.getOrDefault("organization_name", "");
    String tenantName = (String) request.getOrDefault("tenant_name", "");
    String serverDomain = (String) request.getOrDefault("server_domain", "");
    String databaseString = (String) request.getOrDefault("database", "");
    String serverConfig = (String) request.get("server_configuration");
    TenantIdentifier tenantIdentifier = new TenantIdentifier(UUID.randomUUID().toString());
    TenantDomain tenantDomain = new TenantDomain(serverDomain + "/" + tenantIdentifier.value());
    Map<String, Object> tenantAttributes = Map.of("database", DatabaseType.of(databaseString));

    String replacedConfig = serverConfig.replace("ISSUER", tenantDomain.value()).replace("TENANT_ID", tenantIdentifier.value());

    ServerConfiguration serverConfiguration = jsonConverter.read(replacedConfig, ServerConfiguration.class);

    Organization organization = new Organization(new OrganizationIdentifier(UUID.randomUUID().toString()), new OrganizationName(organizationName), new OrganizationDescription(""));

    Tenant tenant = new Tenant(tenantIdentifier, new TenantName(tenantName), TenantType.PUBLIC, tenantDomain);
    organization.assign(tenant);

    tenantRepository.register(tenant);
    serverConfigurationRepository.register(tenant, serverConfiguration);
    organizationRepository.register(tenant, organization);

    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", organization.toMap());
    operator.setCustomProperties(newCustomProperties);

    Tenant admin = tenantRepository.getAdmin();
    userRegistrator.registerOrUpdate(admin, operator);

    return organization.toMap();
  }
}
