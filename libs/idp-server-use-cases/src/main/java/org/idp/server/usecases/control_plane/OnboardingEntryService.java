package org.idp.server.usecases.control_plane;

import java.util.HashMap;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.onboarding.OnboardingApi;
import org.idp.server.control_plane.onboarding.OnboardingContext;
import org.idp.server.control_plane.onboarding.OnboardingContextCreator;
import org.idp.server.control_plane.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.onboarding.validator.OnboardingRequestValidationResult;
import org.idp.server.control_plane.onboarding.validator.OnboardingRequestValidator;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRegistrator;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.organization.*;
import org.idp.server.core.multi_tenancy.tenant.*;
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

  public OnboardingResponse onboard(
      TenantIdentifier adminTenantIdentifier, User operator, OnboardingRequest request) {

    OnboardingRequestValidator validator = new OnboardingRequestValidator(request);
    OnboardingRequestValidationResult validationResult = validator.validate();
    if (!validationResult.isValid()) {
      return validationResult.errorResponse();
    }

    OnboardingContextCreator contextCreator = new OnboardingContextCreator(request, operator);
    OnboardingContext context = contextCreator.create();

    Tenant tenant = context.tenant();
    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationRepository.register(
        tenant, context.authorizationServerConfiguration());
    organizationRepository.register(tenant, context.organization());

    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", context.organization().toMap());
    operator.setCustomProperties(newCustomProperties);

    Tenant admin = tenantQueryRepository.getAdmin();
    userRegistrator.registerOrUpdate(admin, operator);

    return context.toResponse();
  }
}
