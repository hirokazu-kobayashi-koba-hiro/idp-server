package org.idp.server.usecases;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.identity.UserStatus;
import org.idp.server.core.identity.verification.*;
import org.idp.server.core.identity.verification.application.*;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.identity.verification.handler.IdentityVerificationHandler;
import org.idp.server.core.identity.verification.io.IdentityVerificationDynamicResponseMapper;
import org.idp.server.core.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.identity.verification.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.identity.verification.validation.IdentityVerificationRequestValidator;
import org.idp.server.core.identity.verification.validation.IdentityVerificationValidationResult;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.security.event.TokenEventPublisher;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.basic.type.security.RequestAttributes;

@Transaction
public class IdentityVerificationEntryService implements IdentityVerificationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  IdentityVerificationHandler identityVerificationHandler;
  TenantRepository tenantRepository;
  UserRepository userRepository;
  TokenEventPublisher eventPublisher;

  public IdentityVerificationEntryService(
      IdentityVerificationConfigurationQueryRepository configurationQueryRepository,
      IdentityVerificationApplicationCommandRepository applicationCommandRepository,
      IdentityVerificationApplicationQueryRepository applicationQueryRepository,
      IdentityVerificationResultCommandRepository resultCommandRepository,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      TokenEventPublisher eventPublisher) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.applicationCommandRepository = applicationCommandRepository;
    this.applicationQueryRepository = applicationQueryRepository;
    this.tenantRepository = tenantRepository;
    this.resultCommandRepository = resultCommandRepository;
    this.userRepository = userRepository;
    this.identityVerificationHandler = new IdentityVerificationHandler();
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationApplications applications =
        applicationQueryRepository.findAll(tenant, user);

    ExternalWorkflowApplyingResult applyingResult =
        identityVerificationHandler.handleRequest(
            tenant, user, applications, type, process, request, verificationConfiguration);
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
            type,
            request,
            verificationConfiguration.externalWorkflowDelegation(),
            applyingResult,
            process,
            verificationConfiguration);
    applicationCommandRepository.register(tenant, application);
    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_apply,
        requestAttributes);

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            application,
            applyingResult.externalWorkflowResponse(),
            process,
            verificationConfiguration);

    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse findApplications(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    IdentityVerificationApplications applications =
        applicationQueryRepository.findList(tenant, user, queries);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_findList,
        requestAttributes);

    Map<String, Object> response = new HashMap<>();
    response.put("list", applications.toList());
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse process(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, identifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationApplications applications =
        applicationQueryRepository.findAll(tenant, user);

    ExternalWorkflowApplyingResult applyingResult =
        identityVerificationHandler.handleRequest(
            tenant, user, applications, type, process, request, verificationConfiguration);
    if (applyingResult.isError()) {

      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_failure,
          requestAttributes);

      return applyingResult.errorResponse();
    }

    IdentityVerificationApplication updated =
        application.updateProcess(process, request, applyingResult, verificationConfiguration);
    applicationCommandRepository.update(tenant, updated);
    eventPublisher.publish(tenant, oAuthToken, type, process, true, requestAttributes);

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            application,
            applyingResult.externalWorkflowResponse(),
            process,
            verificationConfiguration);
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse callbackExaminationForStaticPath(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);

    IdentityVerificationProcess process =
        ReservedIdentityVerificationProcess.CALLBACK_EXAMINATION.toProcess();
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationRequestValidator applicationValidator =
        new IdentityVerificationRequestValidator(processConfiguration, request);
    IdentityVerificationValidationResult validationResult = applicationValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    ExternalWorkflowApplicationIdentifier externalWorkflowApplicationIdentifier =
        new ExternalWorkflowApplicationIdentifier(
            request.getValueAsString(
                verificationConfiguration.externalWorkflowApplicationIdParam().value()));
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, externalWorkflowApplicationIdentifier);

    IdentityVerificationApplication updatedExamination =
        application.updateExamination(process, request, verificationConfiguration);
    applicationCommandRepository.update(tenant, updatedExamination);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse callbackResultForStaticPath(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationProcess process =
        ReservedIdentityVerificationProcess.CALLBACK_RESULT.toProcess();
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationRequestValidator applicationValidator =
        new IdentityVerificationRequestValidator(processConfiguration, request);
    IdentityVerificationValidationResult validationResult = applicationValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    ExternalWorkflowApplicationIdentifier externalWorkflowApplicationIdentifier =
        new ExternalWorkflowApplicationIdentifier(
            request.getValueAsString(
                verificationConfiguration.externalWorkflowApplicationIdParam().value()));
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, externalWorkflowApplicationIdentifier);

    IdentityVerificationApplication updatedExamination =
        application.completeExamination(process, request, verificationConfiguration);
    applicationCommandRepository.update(tenant, updatedExamination);

    IdentityVerificationResult identityVerificationResult =
        IdentityVerificationResult.create(updatedExamination, request, verificationConfiguration);
    resultCommandRepository.register(tenant, identityVerificationResult);

    // TODO dynamic lifecycle management
    User user = userRepository.get(tenant, application.userId());
    User verifiedUser =
        user.transitStatus(UserStatus.IDENTITY_VERIFIED)
            .setVerifiedClaims(identityVerificationResult.verifiedClaims().toMap());

    userRepository.update(tenant, verifiedUser);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationResponse.OK(response);
  }
}
