package org.idp.server.core.authentication.sms;

import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class SmsAuthenticationChallengeInteractor implements AuthenticationInteractor {

  SmsAuthenticationExecutors executors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public SmsAuthenticationChallengeInteractor(
      SmsAuthenticationExecutors executors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.executors = executors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {
    try {
      SmsAuthenticationConfiguration configuration =
          configurationQueryRepository.get(tenant, "sms", SmsAuthenticationConfiguration.class);
      SmsAuthenticationExecutor executor = executors.get(configuration.type());

      // TODO dynamic specify provider
      User user =
          userQueryRepository.findByPhone(
              tenant, request.getValueAsString("phone_number"), "idp-server");

      if (!user.exists()) {
        Map<String, Object> response =
            Map.of("error", "invalid_request", "error_description", "user not found");
        return AuthenticationInteractionRequestResult.clientError(
            response, type, DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      SmsAuthenticationExecutionRequest executionRequest =
          new SmsAuthenticationExecutionRequest(request.toMap());
      SmsAuthenticationExecutionResult executionResult =
          executor.challenge(tenant, authorizationIdentifier, executionRequest, configuration);

      if (executionResult.isClientError()) {
        return AuthenticationInteractionRequestResult.clientError(
            executionResult.contents(),
            type,
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      if (executionResult.isServerError()) {
        return AuthenticationInteractionRequestResult.serverError(
            executionResult.contents(),
            type,
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          user,
          new Authentication(),
          executionResult.contents(),
          DefaultSecurityEventType.sms_verification_challenge_success);
    } catch (UserTooManyFoundResultException tooManyFoundResultException) {

      Map<String, Object> response =
          Map.of(
              "error",
              "invalid_request",
              "error_description",
              "too many users found for phone number: " + request.getValueAsString("phone_number"));
      return AuthenticationInteractionRequestResult.clientError(
          response, type, DefaultSecurityEventType.sms_verification_challenge_failure);
    }
  }
}
