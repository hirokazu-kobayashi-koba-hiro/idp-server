package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.api.IdpServerStarterApi;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.admin.OrganizationRegistrationRequest;
import org.idp.server.core.handler.admin.TenantRegistrationRequest;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantDomain;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.tenant.TenantType;
import org.idp.server.core.user.PasswordEncodeDelegation;
import org.idp.server.core.user.UserRepository;

@Transactional
public class IdpServerStarterEntryService implements IdpServerStarterApi {

  OrganizationRepository organizationRepository;
  TenantRepository tenantRepository;
  UserRepository userRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public IdpServerStarterEntryService(
      OrganizationRepository organizationRepository,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.organizationRepository = organizationRepository;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public Map<String, Object> initialize(Map<String, Object> request) {
    User user = jsonConverter.read(request.get("user"), User.class);
    String encode = passwordEncodeDelegation.encode(user.rawPassword());
    user.setHashedPassword(encode);

    OrganizationRegistrationRequest organizationRequest =
        jsonConverter.read(request.get("organization"), OrganizationRegistrationRequest.class);
    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    ServerConfiguration serverConfiguration =
        jsonConverter.read(request.get("server_configuration"), ServerConfiguration.class);

    Organization organization = organizationRequest.toOrganization();
    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.ADMIN,
            new TenantDomain(serverConfiguration.tokenIssuer().value()));
    organization.assign(tenant);

    tenantRepository.register(tenant);
    serverConfigurationRepository.register(serverConfiguration);
    organizationRepository.register(organization);
    userRepository.register(tenant, user);

    return Map.of("organization", organization.toMap(), "tenant", tenant.toMap());
  }
}
