package org.idp.server.core;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.trustframework.*;
import org.idp.server.core.identity.trustframework.application.*;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.identity.trustframework.delegation.ExternalWorkflowDelegationClient;
import org.idp.server.core.identity.trustframework.delegation.WorkflowApplyingResult;
import org.idp.server.core.identity.trustframework.validation.IdentityVerificationApplicationValidationResult;
import org.idp.server.core.identity.trustframework.validation.IdentityVerificationApplicationValidator;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.security.event.TokenEventPublisher;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.security.RequestAttributes;

@Transaction
public class IdentityVerificationEntryService implements IdentityVerificationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  ExternalWorkflowDelegationClient externalWorkflowDelegationClient;
  TenantRepository tenantRepository;
  TokenEventPublisher eventPublisher;

  public IdentityVerificationEntryService(
      IdentityVerificationConfigurationQueryRepository configurationQueryRepository,
      IdentityVerificationApplicationCommandRepository applicationCommandRepository,
      IdentityVerificationApplicationQueryRepository applicationQueryRepository,
      TenantRepository tenantRepository,
      TokenEventPublisher eventPublisher) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.applicationCommandRepository = applicationCommandRepository;
    this.applicationQueryRepository = applicationQueryRepository;
    this.tenantRepository = tenantRepository;
    this.externalWorkflowDelegationClient = new ExternalWorkflowDelegationClient();
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationApplicationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, identityVerificationType);
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(identityVerificationProcess);

    IdentityVerificationApplicationValidator applicationValidator =
        new IdentityVerificationApplicationValidator(processConfiguration, request);
    IdentityVerificationApplicationValidationResult validationResult =
        applicationValidator.validate();

    if (validationResult.isError()) {

      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_failure,
          requestAttributes);
      return validationResult.errorResponse();
    }

    WorkflowApplyingResult applyingResult =
        externalWorkflowDelegationClient.execute(request, processConfiguration);

    IdentityVerificationApplication application =
        IdentityVerificationApplication.create(
            tenant,
            oAuthToken.requestedClientId(),
            user,
            identityVerificationType,
            request,
            applyingResult);
    applicationCommandRepository.register(tenant, application);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_apply,
        requestAttributes);

    Map<String, Object> response = new HashMap<>();
    response.put("id", application.externalApplicationId());
    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public IdentityVerificationApplicationResponse process(TenantIdentifier tenantIdentifier, User user, OAuthToken oAuthToken, IdentityVerificationApplicationIdentifier identifier, IdentityVerificationType identityVerificationType, IdentityVerificationProcess identityVerificationProcess, IdentityVerificationApplicationRequest request, RequestAttributes requestAttributes) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
            configurationQueryRepository.get(tenant, identityVerificationType);
    IdentityVerificationProcessConfiguration processConfiguration =
            verificationConfiguration.getProcessConfig(identityVerificationProcess);

    IdentityVerificationApplicationValidator applicationValidator =
            new IdentityVerificationApplicationValidator(processConfiguration, request);
    IdentityVerificationApplicationValidationResult validationResult =
            applicationValidator.validate();

    if (validationResult.isError()) {

      eventPublisher.publish(
              tenant,
              oAuthToken,
              identityVerificationType,
              identityVerificationProcess,
              false,
              requestAttributes);
      return validationResult.errorResponse();
    }

    IdentityVerificationApplication application = applicationQueryRepository.get(tenant, identifier);

    WorkflowApplyingResult applyingResult =
            externalWorkflowDelegationClient.execute(request, processConfiguration);

    IdentityVerificationApplication updated = application.update(identityVerificationProcess, request, applyingResult);

    applicationCommandRepository.update(tenant, updated);

    eventPublisher.publish(
            tenant,
            oAuthToken,
            identityVerificationType,
            identityVerificationProcess,
            true,
            requestAttributes);

    Map<String, Object> response = new HashMap<>();
    response.put("application_id", application.externalApplicationId());
    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public void callbackProcess() {}

  @Override
  public void callbackResult() {}
}
