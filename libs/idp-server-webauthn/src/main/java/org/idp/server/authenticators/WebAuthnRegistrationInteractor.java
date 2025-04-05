package org.idp.server.authenticators;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authenticators.webauthn.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.mfa.*;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnRegistrationInteractor implements MfaInteractor {

  MfaConfigurationQueryRepository configurationRepository;
  MfaTransactionQueryRepository transactionQueryRepository;
  WebAuthnCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthnRegistrationInteractor(
      MfaConfigurationQueryRepository configurationRepository,
      MfaTransactionQueryRepository transactionQueryRepository,
      WebAuthnCredentialRepository credentialRepository) {
    this.configurationRepository = configurationRepository;
    this.transactionQueryRepository = transactionQueryRepository;
    this.credentialRepository = credentialRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      MfaTransactionIdentifier mfaTransactionIdentifier,
      MfaInteractionType type,
      MfaInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    String requestString = jsonConverter.write(request.toMap());
    String userId = oAuthSession.user().sub();

    WebAuthnConfiguration configuration =
        configurationRepository.get(tenant, "webauthn", WebAuthnConfiguration.class);
    SerializableWebAuthnSession serializableWebAuthnSession =
        transactionQueryRepository.get(
            mfaTransactionIdentifier, "webauthn", SerializableWebAuthnSession.class);

    WebAuthnRegistrationManager manager =
        new WebAuthnRegistrationManager(
            configuration, serializableWebAuthnSession.toWebAuthnSession(), requestString, userId);

    WebAuthnCredential webAuthnCredential = manager.verifyAndCreateCredential();

    credentialRepository.register(webAuthnCredential);

    Map<String, Object> response = new HashMap<>();
    response.put("registration", webAuthnCredential.toMap());

    return new MfaInteractionResult(
        MfaInteractionStatus.SUCCESS,
        type,
        oAuthSession.user(),
        new Authentication(),
        response,
        DefaultSecurityEventType.webauthn_registration_success);
  }
}
