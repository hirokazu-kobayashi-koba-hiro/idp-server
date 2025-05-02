package org.idp.server.core.authentication.webauthn;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public class WebAuthnAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  WebAuthnExecutors webAuthnExecutors;

  public WebAuthnAuthenticationChallengeInteractor(
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

    WebAuthnConfiguration configuration =
        configurationRepository.get(tenant, "webauthn", WebAuthnConfiguration.class);

    WebAuthnExecutor webAuthnExecutor = webAuthnExecutors.get(configuration.type());
    WebAuthnChallenge webAuthnChallenge =
        webAuthnExecutor.challengeAuthentication(
            tenant, authorizationIdentifier, request, configuration);

    Map<String, Object> response = new HashMap<>();
    response.put("challenge", webAuthnChallenge.challenge());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        transaction.user(),
        new Authentication(),
        response,
        DefaultSecurityEventType.webauthn_authentication_challenge);
  }
}
