package org.idp.server.core.authentication.sms;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public class SmsAuthenticationRegistrationInteractor implements AuthenticationInteractor {

  SmsAuthenticationExecutors executors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public SmsAuthenticationRegistrationInteractor(
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
      UserRepository userRepository) {
    SmsAuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, "sms", SmsAuthenticationConfiguration.class);
    SmsAuthenticationExecutor executor = executors.get(configuration.type());

    SmsAuthenticationExecutionRequest executionRequest =
        new SmsAuthenticationExecutionRequest(request.toMap());
    SmsAuthenticationExecutionResult executionResult =
        executor.verify(tenant, authorizationIdentifier, executionRequest, configuration);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(), type, DefaultSecurityEventType.sms_verification_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(), type, DefaultSecurityEventType.sms_verification_failure);
    }

    User user = transaction.user();
    User phoneNumberVerifiedUser = user.setPhoneNumberVerified(true);

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("opt")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        phoneNumberVerifiedUser,
        authentication,
        executionResult.contents(),
        DefaultSecurityEventType.sms_verification_success);
  }
}
