package org.idp.server.usecases.control_plane;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.starter.IdpServerStarterApi;
import org.idp.server.control_plane.starter.IdpServerStarterContext;
import org.idp.server.control_plane.starter.IdpServerStarterContextCreator;
import org.idp.server.control_plane.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.starter.io.IdpServerStarterResponse;
import org.idp.server.control_plane.starter.validator.IdpServerInitializeRequestValidationResult;
import org.idp.server.control_plane.starter.validator.IdpServerInitializeRequestValidator;
import org.idp.server.control_plane.starter.verifier.IdpServerStarterVerifier;
import org.idp.server.control_plane.starter.verifier.IdpServerVerificationResult;
import org.idp.server.control_plane.starter.verifier.StarterTenantVerifier;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.permission.PermissionCommandRepository;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.role.RoleCommandRepository;
import org.idp.server.core.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;

@Transaction
public class IdpServerStarterEntryService implements IdpServerStarterApi {

  OrganizationRepository organizationRepository;
  TenantCommandRepository tenantCommandRepository;
  UserCommandRepository userCommandRepository;
  PermissionCommandRepository permissionCommandRepository;
  RoleCommandRepository roleCommandRepository;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  IdpServerStarterVerifier starterVerifier;

  public IdpServerStarterEntryService(
      OrganizationRepository organizationRepository,
      TenantQueryRepository tenantQueryRepository,
      TenantCommandRepository tenantCommandRepository,
      UserCommandRepository userCommandRepository,
      PermissionCommandRepository permissionCommandRepository,
      RoleCommandRepository roleCommandRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.organizationRepository = organizationRepository;
    this.tenantCommandRepository = tenantCommandRepository;
    this.userCommandRepository = userCommandRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    StarterTenantVerifier starterTenantVerifier = new StarterTenantVerifier(tenantQueryRepository);
    this.starterVerifier = new IdpServerStarterVerifier(starterTenantVerifier);
  }

  @Override
  public IdpServerStarterResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      IdpServerStarterRequest request,
      RequestAttributes requestAttributes) {

    IdpServerInitializeRequestValidator requestValidator =
        new IdpServerInitializeRequestValidator(request);
    IdpServerInitializeRequestValidationResult validated = requestValidator.validate();
    if (!validated.isValid()) {
      return validated.errorResponse();
    }

    IdpServerStarterContextCreator contextCreator =
        new IdpServerStarterContextCreator(request, passwordEncodeDelegation);
    IdpServerStarterContext context = contextCreator.create();

    IdpServerVerificationResult verificationResult = starterVerifier.verify(context);
    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (request.isDryRun()) {
      return context.toResponse();
    }

    Tenant tenant = context.tenant();
    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationRepository.register(
        tenant, context.authorizationServerConfiguration());
    organizationRepository.register(tenant, context.organization());
    permissionCommandRepository.bulkRegister(tenant, context.permissions());
    roleCommandRepository.bulkRegister(tenant, context.roles());
    userCommandRepository.register(tenant, context.user());
    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return context.toResponse();
  }
}
