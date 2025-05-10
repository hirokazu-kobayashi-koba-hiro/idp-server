package org.idp.server.usecases.control_plane;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.OnboardingApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRegistrator;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.organization.*;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;

@Transaction
public class OnboardingEntryService implements OnboardingApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  JsonConverter jsonConverter;

  public OnboardingEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository, userCommandRepository);
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  // TODO improve logic
  public Map<String, Object> initialize(
      TenantIdentifier adminTenantIdentifier, User operator, Map<String, Object> request) {

    String organizationName = (String) request.getOrDefault("organization_name", "");
    String tenantName = (String) request.getOrDefault("tenant_name", "");
    String serverDomain = (String) request.getOrDefault("server_domain", "");
    String databaseString = (String) request.getOrDefault("database", "");
    String serverConfig = (String) request.get("authorization_server_configuration");
    TenantIdentifier tenantIdentifier = new TenantIdentifier(UUID.randomUUID().toString());
    TenantDomain tenantDomain = new TenantDomain(serverDomain + "/" + tenantIdentifier.value());
    AuthorizationProvider authorizationProvider =
        DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
    DatabaseType databaseType = DatabaseType.of(databaseString);

    String replacedConfig =
        serverConfig
            .replace("ISSUER", tenantDomain.value())
            .replace("TENANT_ID", tenantIdentifier.value());

    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(replacedConfig, AuthorizationServerConfiguration.class);

    Organization organization =
        new Organization(
            new OrganizationIdentifier(UUID.randomUUID().toString()),
            new OrganizationName(organizationName),
            new OrganizationDescription(""));

    Tenant tenant =
        new Tenant(
            tenantIdentifier,
            new TenantName(tenantName),
            TenantType.PUBLIC,
            tenantDomain,
            authorizationProvider,
            databaseType,
            TenantAttributes.createDefaultType());
    organization.assign(tenant);

    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationRepository.register(tenant, authorizationServerConfiguration);
    organizationRepository.register(tenant, organization);

    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", organization.toMap());
    operator.setCustomProperties(newCustomProperties);

    Tenant admin = tenantQueryRepository.getAdmin();
    userRegistrator.registerOrUpdate(admin, operator);

    return organization.toMap();
  }
}
