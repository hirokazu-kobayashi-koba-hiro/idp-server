package org.idp.server.core.authentication.fidouaf;

import org.idp.server.core.authentication.*;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class FidoUafRegistrationInteractor implements AuthenticationInteractor {

  FidoUafExecutors fidoUafExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public FidoUafRegistrationInteractor(
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
      UserRepository userRepository) {

    FidoUafConfiguration fidoUafConfiguration =
        configurationQueryRepository.get(tenant, "fido-uaf", FidoUafConfiguration.class);
    FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

    FidoUafExecutionRequest fidoUafExecutionRequest = new FidoUafExecutionRequest(request.toMap());
    FidoUafExecutionResult executionResult =
        fidoUafExecutor.verifyRegistration(
            tenant, authorizationIdentifier, fidoUafExecutionRequest, fidoUafConfiguration);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(), type, DefaultSecurityEventType.fido_uaf_registration_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(), type, DefaultSecurityEventType.fido_uaf_registration_failure);
    }

    String authenticationDeviceId = executionResult.getValueAsStringFromContents("user_id");
    User user = transaction.user();
    AuthenticationDevice authenticationDevice =
        new AuthenticationDevice(authenticationDeviceId, "", "", "", "", "", true);
    User addedDeviceUser = user.addAuthenticationDevice(authenticationDevice);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        addedDeviceUser,
        new Authentication(),
        executionResult.contents(),
        DefaultSecurityEventType.fido_uaf_registration_success);
  }
}
