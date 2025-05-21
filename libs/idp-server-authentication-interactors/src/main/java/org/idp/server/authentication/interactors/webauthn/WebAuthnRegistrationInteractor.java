package org.idp.server.authentication.interactors.webauthn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

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
      UserQueryRepository userQueryRepository) {

    String userId = transaction.user().sub();
    WebAuthnConfiguration configuration =
        configurationRepository.get(tenant, "webauthn", WebAuthnConfiguration.class);
    WebAuthnExecutor webAuthnExecutor = webAuthnExecutors.get(configuration.type());
    WebAuthnVerificationResult webAuthnVerificationResult =
        webAuthnExecutor.verifyRegistration(
            tenant, authorizationIdentifier, userId, request, configuration);

    Map<String, Object> response = new HashMap<>();
    response.put("registration", webAuthnVerificationResult.toMap());

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("hwk")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        transaction.user(),
        authentication,
        response,
        DefaultSecurityEventType.webauthn_registration_success);
  }
}
