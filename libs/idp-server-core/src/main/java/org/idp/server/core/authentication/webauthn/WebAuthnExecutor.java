package org.idp.server.core.authentication.webauthn;

import org.idp.server.core.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface WebAuthnExecutor {

  WebAuthnExecutorType type();

  WebAuthnChallenge challengeRegistration(Tenant tenant, AuthorizationIdentifier authorizationIdentifier, AuthenticationInteractionRequest request, WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyRegistration(Tenant tenant, AuthorizationIdentifier authorizationIdentifier, String userId, AuthenticationInteractionRequest request, WebAuthnConfiguration configuration);

  WebAuthnChallenge challengeAuthentication(Tenant tenant, AuthorizationIdentifier authorizationIdentifier, AuthenticationInteractionRequest request, WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyAuthentication(Tenant tenant, AuthorizationIdentifier authorizationIdentifier, AuthenticationInteractionRequest request, WebAuthnConfiguration configuration);
}
