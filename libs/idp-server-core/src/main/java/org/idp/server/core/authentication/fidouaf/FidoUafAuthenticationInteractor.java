package org.idp.server.core.authentication.fidouaf;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class FidoUafAuthenticationInteractor implements AuthenticationInteractor {

  FidoUafExecutors fidoUafExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public FidoUafAuthenticationInteractor(
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
        fidoUafExecutor.verifyAuthentication(
            tenant, authorizationIdentifier, fidoUafExecutionRequest, fidoUafConfiguration);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(),
          type,
          DefaultSecurityEventType.fido_uaf_authentication_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(),
          type,
          DefaultSecurityEventType.fido_uaf_authentication_failure);
    }

    // TODO
    String deviceId = executionResult.getValueAsStringFromContents("user_id");
    User user = userRepository.findByAuthenticationDevice(tenant, deviceId);
    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("hwk")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        executionResult.contents(),
        DefaultSecurityEventType.fido_uaf_authentication_success);
  }
}
