package org.idp.server.authentication.interactors.webauthn;

import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface WebAuthnExecutor {

  WebAuthnExecutorType type();

  WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnChallenge challengeAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);
}
