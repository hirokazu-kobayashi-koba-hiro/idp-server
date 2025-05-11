package org.idp.server.usecases.control_plane;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.onboarding.OnboardingContext;
import org.idp.server.control_plane.management.onboarding.OnboardingContextCreator;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.validator.OnboardingRequestValidationResult;
import org.idp.server.control_plane.management.onboarding.validator.OnboardingRequestValidator;
import org.idp.server.control_plane.management.onboarding.verifier.OnboardingVerificationResult;
import org.idp.server.control_plane.management.onboarding.verifier.OnboardingVerifier;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRegistrator;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.organization.*;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;

@Transaction
public class OnboardingEntryService implements OnboardingApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  OnboardingVerifier onboardingVerifier;

  public OnboardingEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository, userCommandRepository);
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    this.onboardingVerifier = new OnboardingVerifier(tenantVerifier);
  }

  public OnboardingResponse onboard(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OnboardingRequest request,
      RequestAttributes requestAttributes) {

    OnboardingRequestValidator validator = new OnboardingRequestValidator(request);
    OnboardingRequestValidationResult validationResult = validator.validate();
    if (!validationResult.isValid()) {
      return validationResult.errorResponse();
    }

    OnboardingContextCreator contextCreator = new OnboardingContextCreator(request, operator);
    OnboardingContext context = contextCreator.create();

    OnboardingVerificationResult verificationResult = onboardingVerifier.verify(context);
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
