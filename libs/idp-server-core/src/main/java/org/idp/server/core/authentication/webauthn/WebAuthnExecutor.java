package org.idp.server.core.authentication.webauthn;

import org.idp.server.core.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;

public interface WebAuthnExecutor {

  WebAuthnExecutorType type();

  WebAuthnChallenge challengeRegistration(
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyRegistration(
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnChallenge challengeAuthentication(
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyAuthentication(
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);
}
