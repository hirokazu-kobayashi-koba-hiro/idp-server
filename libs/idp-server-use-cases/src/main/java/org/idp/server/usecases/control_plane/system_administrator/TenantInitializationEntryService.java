package org.idp.server.usecases.control_plane.system_administrator;

import org.idp.server.control_plane.admin.tenant.TenantInitializationApi;
import org.idp.server.control_plane.admin.tenant.TenantInitializationContext;
import org.idp.server.control_plane.admin.tenant.TenantInitializationContextCreator;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationResponse;
import org.idp.server.control_plane.admin.tenant.validator.TenantInitializeRequestValidationResult;
import org.idp.server.control_plane.admin.tenant.validator.TenantInitializeRequestValidator;
import org.idp.server.control_plane.admin.tenant.verifier.TenantInitializationVerificationResult;
import org.idp.server.control_plane.admin.tenant.verifier.TenantInitializationVerifier;
import org.idp.server.control_plane.base.verifier.ClientVerifier;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.UserRegistrator;
import org.idp.server.core.oidc.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class TenantInitializationEntryService implements TenantInitializationApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  TenantInitializationVerifier tenantInitializationVerifier;
  PasswordEncodeDelegation passwordEncodeDelegation;

  public TenantInitializationEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository, userCommandRepository);
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    ClientVerifier clientVerifier = new ClientVerifier(clientConfigurationQueryRepository);
    this.tenantInitializationVerifier =
        new TenantInitializationVerifier(tenantVerifier, clientVerifier);
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  @Override
  public TenantInitializationResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      TenantInitializationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    TenantInitializeRequestValidator validator =
        new TenantInitializeRequestValidator(request, dryRun);
    TenantInitializeRequestValidationResult validationResult = validator.validate();
    if (!validationResult.isValid()) {
      return validationResult.errorResponse();
    }

    TenantInitializationContextCreator contextCreator =
        new TenantInitializationContextCreator(request, dryRun, passwordEncodeDelegation);
    TenantInitializationContext context = contextCreator.create();

    TenantInitializationVerificationResult verificationResult =
        tenantInitializationVerifier.verify(context);

    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    Tenant tenant = context.tenant();
    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationCommandRepository.register(
        tenant, context.authorizationServerConfiguration());
    organizationRepository.register(tenant, context.organization());
    Tenant admin = tenantQueryRepository.getAdmin();
    userRegistrator.registerOrUpdate(admin, context.user());
    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return context.toResponse();
  }
}
