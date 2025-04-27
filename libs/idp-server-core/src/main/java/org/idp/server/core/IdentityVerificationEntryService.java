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
import org.idp.server.core.identity.trustframework.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.identity.trustframework.delegation.ExternalWorkflowDelegationClient;
import org.idp.server.core.identity.trustframework.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.identity.trustframework.validation.IdentityVerificationRequestValidator;
import org.idp.server.core.identity.trustframework.validation.IdentityVerificationValidationResult;
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
  IdentityVerificationResultCommandRepository resultCommandRepository;
  ExternalWorkflowDelegationClient externalWorkflowDelegationClient;
  TenantRepository tenantRepository;
  TokenEventPublisher eventPublisher;

  public IdentityVerificationEntryService(
      IdentityVerificationConfigurationQueryRepository configurationQueryRepository,
      IdentityVerificationApplicationCommandRepository applicationCommandRepository,
      IdentityVerificationApplicationQueryRepository applicationQueryRepository,
      IdentityVerificationResultCommandRepository resultCommandRepository,
      TenantRepository tenantRepository,
      TokenEventPublisher eventPublisher) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.applicationCommandRepository = applicationCommandRepository;
    this.applicationQueryRepository = applicationQueryRepository;
    this.tenantRepository = tenantRepository;
    this.resultCommandRepository = resultCommandRepository;
    this.externalWorkflowDelegationClient = new ExternalWorkflowDelegationClient();
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, identityVerificationType);

    ExternalWorkflowApplyingResult applyingResult =
        externalWorkflowDelegationClient.execute(
            request, identityVerificationProcess, verificationConfiguration);
    if (applyingResult.isError()) {

      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_failure,
          requestAttributes);
      return applyingResult.errorResponse();
    }

    IdentityVerificationApplication application =
        IdentityVerificationApplication.create(
            tenant,
            oAuthToken.requestedClientId(),
            user,
            identityVerificationType,
            request,
            verificationConfiguration.externalWorkflowDelegation(),
            applyingResult);

    applicationCommandRepository.register(tenant, application);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_apply,
        requestAttributes);

    Map<String, Object> response = new HashMap<>();
    response.put("id", application.externalApplicationId().value());
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse process(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, identifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, identityVerificationType);

    ExternalWorkflowApplyingResult applyingResult =
        externalWorkflowDelegationClient.execute(
            request, identityVerificationProcess, verificationConfiguration);
    if (applyingResult.isError()) {

      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_failure,
          requestAttributes);

      return applyingResult.errorResponse();
    }

    IdentityVerificationApplication updated =
        application.updateProcess(identityVerificationProcess, request, applyingResult);

    applicationCommandRepository.update(tenant, updated);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        identityVerificationType,
        identityVerificationProcess,
        true,
        requestAttributes);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse callbackExamination(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, identityVerificationType);
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(identityVerificationProcess);

    IdentityVerificationRequestValidator applicationValidator =
        new IdentityVerificationRequestValidator(processConfiguration, request);
    IdentityVerificationValidationResult validationResult = applicationValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, identifier);

    IdentityVerificationApplication updatedExamination =
        application.updateExamination(identityVerificationProcess, request);
    applicationCommandRepository.update(tenant, updatedExamination);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse callbackResult(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      IdentityVerificationProcess identityVerificationProcess,
      IdentityVerificationRequest request) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, identityVerificationType);
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(identityVerificationProcess);

    IdentityVerificationRequestValidator applicationValidator =
        new IdentityVerificationRequestValidator(processConfiguration, request);
    IdentityVerificationValidationResult validationResult = applicationValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, identifier);

    IdentityVerificationApplication updatedExamination =
        application.updateExamination(identityVerificationProcess, request);
    applicationCommandRepository.update(tenant, updatedExamination);

    IdentityVerificationResult identityVerificationResult =
        IdentityVerificationResult.create(updatedExamination, request);
    resultCommandRepository.register(tenant, identityVerificationResult);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationResponse.OK(response);
  }
}
