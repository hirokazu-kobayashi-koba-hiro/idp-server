package org.idp.server.core.authentication.webauthn;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnRegistrationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  WebAuthnExecutors webAuthnExecutors;

  public WebAuthnRegistrationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      WebAuthnExecutors webAuthnExecutors) {
    this.configurationRepository = configurationRepository;
    this.webAuthnExecutors = webAuthnExecutors;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserRepository userRepository) {

    String userId = transaction.user().sub();
    WebAuthnConfiguration configuration =
        configurationRepository.get(tenant, "webauthn", WebAuthnConfiguration.class);
    WebAuthnExecutor webAuthnExecutor = webAuthnExecutors.get(configuration.type());
    WebAuthnVerificationResult webAuthnVerificationResult =
        webAuthnExecutor.verifyRegistration(
            tenant, authorizationIdentifier, userId, request, configuration);

    Map<String, Object> response = new HashMap<>();
    response.put("registration", webAuthnVerificationResult.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        transaction.user(),
        new Authentication(),
        response,
        DefaultSecurityEventType.webauthn_registration_success);
  }
}
