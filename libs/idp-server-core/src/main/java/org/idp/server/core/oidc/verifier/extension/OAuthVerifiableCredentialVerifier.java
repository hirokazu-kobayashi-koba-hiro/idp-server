/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.rar.AuthorizationDetailsInvalidException;
import org.idp.server.core.oidc.vc.VerifiableCredentialInvalidException;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;

public class OAuthVerifiableCredentialVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !(oAuthRequestContext.hasAuthorizationDetails()
        && oAuthRequestContext.authorizationDetails().hasVerifiableCredential());
  }

  @Override
  public void verify(OAuthRequestContext context) {
    try {
      VerifiableCredentialVerifier verifiableCredentialVerifier =
          new VerifiableCredentialVerifier(
              context.authorizationRequest().authorizationDetails(),
              context.serverConfiguration(),
              context.clientConfiguration());
      verifiableCredentialVerifier.verify();
    } catch (AuthorizationDetailsInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          exception.error(), exception.errorDescription(), context);
    } catch (VerifiableCredentialInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          exception.error(), exception.errorDescription(), context);
    }
  }
}
