package org.idp.server.usecases.control_plane;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.tenant.TenantInitializationApi;
import org.idp.server.control_plane.tenant.TenantInitializationContext;
import org.idp.server.control_plane.tenant.TenantInitializationContextCreator;
import org.idp.server.control_plane.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.tenant.io.TenantInitializationResponse;
import org.idp.server.control_plane.tenant.validator.TenantInitializeRequestValidationResult;
import org.idp.server.control_plane.tenant.validator.TenantInitializeRequestValidator;
import org.idp.server.control_plane.tenant.verifier.TenantInitializationVerificationResult;
import org.idp.server.control_plane.tenant.verifier.TenantInitializationVerifier;
import org.idp.server.control_plane.verifier.ClientVerifier;
import org.idp.server.control_plane.verifier.TenantVerifier;
import org.idp.server.core.identity.UserRegistrator;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;

@Transaction
public class TenantInitializationEntryService implements TenantInitializationApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
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
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository, userCommandRepository);
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
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
      RequestAttributes requestAttributes) {
    TenantInitializeRequestValidator validator = new TenantInitializeRequestValidator(request);
    TenantInitializeRequestValidationResult validationResult = validator.validate();
    if (!validationResult.isValid()) {
      return validationResult.errorResponse();
    }

    TenantInitializationContextCreator contextCreator =
        new TenantInitializationContextCreator(request, passwordEncodeDelegation);
    TenantInitializationContext context = contextCreator.create();

    TenantInitializationVerificationResult verificationResult =
        tenantInitializationVerifier.verify(context);

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
    Tenant admin = tenantQueryRepository.getAdmin();
    userRegistrator.registerOrUpdate(admin, context.user());
    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return context.toResponse();
  }
}
