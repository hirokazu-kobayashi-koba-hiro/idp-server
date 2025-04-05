package org.idp.server.authenticators;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authenticators.webauthn.*;
import org.idp.server.core.mfa.*;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnAuthenticationChallengeInteractor implements MfaInteractor {

  MfaConfigurationQueryRepository configurationRepository;
  MfaTransactionCommandRepository transactionCommandRepository;
  WebAuthnCredentialRepository credentialRepository;

  public WebAuthnAuthenticationChallengeInteractor(
      MfaConfigurationQueryRepository configurationRepository,
      MfaTransactionCommandRepository transactionCommandRepository,
      WebAuthnCredentialRepository credentialRepository) {
    this.configurationRepository = configurationRepository;
    this.transactionCommandRepository = transactionCommandRepository;
    this.credentialRepository = credentialRepository;
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      MfaTransactionIdentifier mfaTransactionIdentifier,
      MfaInteractionType type,
      MfaInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    configurationRepository.get(tenant, "webauthn", WebAuthnConfiguration.class);

    WebAuthnChallenge webAuthnChallenge = WebAuthnChallenge.generate();
    WebAuthnSession webAuthnSession = new WebAuthnSession(webAuthnChallenge);

    SerializableWebAuthnSession serializableWebAuthnSession =
        webAuthnSession.toSerializableWebAuthnSession();
    transactionCommandRepository.register(
        mfaTransactionIdentifier, "webauthn", serializableWebAuthnSession);

    Map<String, Object> response = new HashMap<>();
    response.put("challenge", webAuthnSession.challengeAsString());

    return new MfaInteractionResult(
        MfaInteractionStatus.SUCCESS,
        type,
        oAuthSession.user(),
        new Authentication(),
        response,
        DefaultSecurityEventType.webauthn_authentication_challenge);
  }
}
