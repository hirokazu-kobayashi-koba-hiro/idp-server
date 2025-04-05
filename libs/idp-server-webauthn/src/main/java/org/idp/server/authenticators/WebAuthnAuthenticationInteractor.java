package org.idp.server.authenticators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authenticators.webauthn.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.mfa.*;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnAuthenticationInteractor implements MfaInteractor {

  MfaConfigurationQueryRepository configurationRepository;
  MfaTransactionQueryRepository transactionQueryRepository;
  WebAuthnCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthnAuthenticationInteractor(
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

    WebAuthnConfiguration configuration =
        configurationRepository.get(tenant, "webauthn", WebAuthnConfiguration.class);
    SerializableWebAuthnSession serializableWebAuthnSession =
        transactionQueryRepository.get(
            mfaTransactionIdentifier, "webauthn", SerializableWebAuthnSession.class);

    WebAuthnAuthenticationManager manager =
        new WebAuthnAuthenticationManager(
            configuration, serializableWebAuthnSession.toWebAuthnSession(), requestString);

    String extractUserId = manager.extractUserId();
    WebAuthnCredentials webAuthnCredentials = credentialRepository.findAll(extractUserId);

    try {
      manager.verify(webAuthnCredentials);
      User user = userRepository.get(extractUserId);

      Authentication authentication =
          new Authentication()
              .setTime(SystemDateTime.now())
              .addMethods(new ArrayList<>(List.of("hwk")))
              .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

      Map<String, Object> response = new HashMap<>();
      response.put("user", user.toMap());
      response.put("authentication", authentication.toMap());

      return new MfaInteractionResult(
          MfaInteractionStatus.SUCCESS,
          type,
          user,
          authentication,
          response,
          DefaultSecurityEventType.webauthn_authentication_success);
    } catch (WebAuthnBadRequestException e) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "webauthn authentication failed");

      return new MfaInteractionResult(
          MfaInteractionStatus.CLIENT_ERROR,
          type,
          response,
          DefaultSecurityEventType.webauthn_authentication_failure);
    } catch (Exception e) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "unexpected error, webauthn authentication failed.");

      return new MfaInteractionResult(
          MfaInteractionStatus.SERVER_ERROR,
          type,
          response,
          DefaultSecurityEventType.webauthn_authentication_failure);
    }
  }
}
