package org.idp.server.usecases.control_plane;

import java.util.Map;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.IdpServerStarterApi;
import org.idp.server.control_plane.definition.DefinitionReader;
import org.idp.server.control_plane.io.*;
import org.idp.server.control_plane.validator.IdpServerInitializeRequestValidationResult;
import org.idp.server.control_plane.validator.IdpServerInitializeRequestValidator;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserStatus;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.permission.PermissionCommandRepository;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.role.RoleCommandRepository;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;

@Transaction
public class IdpServerStarterEntryService implements IdpServerStarterApi {

  OrganizationRepository organizationRepository;
  TenantCommandRepository tenantCommandRepository;
  UserCommandRepository userCommandRepository;
  PermissionCommandRepository permissionCommandRepository;
  RoleCommandRepository roleCommandRepository;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public IdpServerStarterEntryService(
      OrganizationRepository organizationRepository,
      TenantCommandRepository tenantCommandRepository,
      UserCommandRepository userCommandRepository,
      PermissionCommandRepository permissionCommandRepository,
      RoleCommandRepository roleCommandRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.organizationRepository = organizationRepository;
    this.tenantCommandRepository = tenantCommandRepository;
    this.userCommandRepository = userCommandRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public Map<String, Object> initialize(
      TenantIdentifier adminTenantIdentifier, Map<String, Object> request) {

    IdpServerInitializeRequestValidator requestValidator =
        new IdpServerInitializeRequestValidator(request);
    IdpServerInitializeRequestValidationResult validated = requestValidator.validate();
    if (!validated.isValid()) {
      return validated.errorResponse();
    }

    OrganizationRegistrationRequest organizationRequest =
        jsonConverter.read(request.get("organization"), OrganizationRegistrationRequest.class);
    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(
            request.get("authorization_server_configuration"),
            AuthorizationServerConfiguration.class);

    Permissions permissions = DefinitionReader.permissions();
    ;
    Roles roles = DefinitionReader.roles();

    User user = jsonConverter.read(request.get("user"), User.class);
    String encode = passwordEncodeDelegation.encode(user.rawPassword());
    user.setHashedPassword(encode);
    User updatedUser = user.transitStatus(UserStatus.REGISTERED);

    Organization organization = organizationRequest.toOrganization();
    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.ADMIN,
            new TenantDomain(authorizationServerConfiguration.tokenIssuer().value()),
            DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider(),
            DatabaseType.POSTGRESQL,
            TenantAttributes.createDefaultType());
    organization.assign(tenant);

    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationRepository.register(tenant, authorizationServerConfiguration);
    organizationRepository.register(tenant, organization);
    permissionCommandRepository.bulkRegister(tenant, permissions);
    roleCommandRepository.bulkRegister(tenant, roles);
    userCommandRepository.register(tenant, updatedUser);

    return Map.of("organization", organization.toMap(), "tenant", tenant.toMap());
  }
}
