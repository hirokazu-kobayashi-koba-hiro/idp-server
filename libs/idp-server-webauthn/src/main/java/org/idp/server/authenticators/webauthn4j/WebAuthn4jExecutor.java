package org.idp.server.authenticators.webauthn4j;

import java.util.Map;
import org.idp.server.core.authentication.AuthenticationInteractionCommandRepository;
import org.idp.server.core.authentication.AuthenticationInteractionQueryRepository;
import org.idp.server.core.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.authentication.webauthn.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.tenant.Tenant;

public class WebAuthn4jExecutor implements WebAuthnExecutor {

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthn4jExecutor(
      AuthenticationInteractionCommandRepository transactionCommandRepository,
      AuthenticationInteractionQueryRepository transactionQueryRepository,
      WebAuthn4jCredentialRepository credentialRepository) {
    this.transactionCommandRepository = transactionCommandRepository;
    this.transactionQueryRepository = transactionQueryRepository;
    this.credentialRepository = credentialRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public WebAuthnExecutorType type() {
    return new WebAuthnExecutorType("webauthn4j");
  }

  @Override
  public WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    WebAuthnChallenge webAuthnChallenge = webAuthn4jChallenge.toWebAuthnChallenge();
    transactionCommandRepository.register(
        tenant, authorizationIdentifier, type().value(), webAuthnChallenge);

    return webAuthnChallenge;
  }

  @Override
  public WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthnChallenge webAuthnChallenge =
        transactionQueryRepository.get(
            tenant, authorizationIdentifier, type().value(), WebAuthnChallenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge =
        new WebAuthn4jChallenge(webAuthnChallenge.challenge());
    String requestString = jsonConverter.write(request.toMap());
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.getDetail(type()), WebAuthn4jConfiguration.class);
    WebAuthn4jRegistrationManager manager =
        new WebAuthn4jRegistrationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString, userId);

    WebAuthn4jCredential webAuthn4jCredential = manager.verifyAndCreateCredential();
    credentialRepository.register(webAuthn4jCredential);

    return new WebAuthnVerificationResult(Map.of("registration", webAuthn4jCredential.toMap()));
  }

  @Override
  public WebAuthnChallenge challengeAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    WebAuthnChallenge webAuthnChallenge = webAuthn4jChallenge.toWebAuthnChallenge();
    transactionCommandRepository.register(
        tenant, authorizationIdentifier, type().value(), webAuthnChallenge);

    return webAuthnChallenge;
  }

  @Override
  public WebAuthnVerificationResult verifyAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthnChallenge webAuthnChallenge =
        transactionQueryRepository.get(
            tenant, authorizationIdentifier, type().value(), WebAuthnChallenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge =
        new WebAuthn4jChallenge(webAuthnChallenge.challenge());
    String requestString = jsonConverter.write(request.toMap());
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.getDetail(type()), WebAuthn4jConfiguration.class);
    WebAuthn4jAuthenticationManager manager =
        new WebAuthn4jAuthenticationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString);

    String extractUserId = manager.extractUserId();
    WebAuthn4jCredentials webAuthn4jCredentials = credentialRepository.findAll(extractUserId);

    manager.verify(webAuthn4jCredentials);

    return new WebAuthnVerificationResult(Map.of("user_id", extractUserId));
  }
}
