package org.idp.server.authentication.interactors.fidouaf;

import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class FidoUafRegistrationChallengeInteractor implements AuthenticationInteractor {

  FidoUafExecutors fidoUafExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public FidoUafRegistrationChallengeInteractor(
      FidoUafExecutors fidoUafExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.fidoUafExecutors = fidoUafExecutors;
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

    FidoUafConfiguration fidoUafConfiguration =
        configurationQueryRepository.get(tenant, "fido-uaf", FidoUafConfiguration.class);
    FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

    AuthenticationDeviceIdentifier authenticationDeviceIdentifier =
        AuthenticationDeviceIdentifier.generate();
    FidoUafExecutionRequest fidoUafExecutionRequest =
        new FidoUafExecutionRequest(
            Map.of(fidoUafConfiguration.deviceIdParam(), authenticationDeviceIdentifier.value()));
    FidoUafExecutionResult executionResult =
        fidoUafExecutor.challengeRegistration(
            tenant, authorizationIdentifier, fidoUafExecutionRequest, fidoUafConfiguration);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(),
          type,
          DefaultSecurityEventType.fido_uaf_registration_challenge_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(),
          type,
          DefaultSecurityEventType.fido_uaf_registration_challenge_failure);
    }

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        transaction.user(),
        new Authentication(),
        executionResult.contents(),
        DefaultSecurityEventType.fido_uaf_registration_challenge_success);
  }
}
