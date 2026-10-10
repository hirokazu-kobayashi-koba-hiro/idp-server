/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.idp.server.core.extension.oid4vci.verifier;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetailsInvalidException;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.core.openid.oauth.verifier.extension.VerifiableCredentialVerifier;

/**
 * Verifies {@code openid_credential} authorization details of an authorization request (OpenID4VCI
 * 1.0 Section 5.1.1), and reports a failure to the client through the redirect.
 */
public class OpenIdCredentialAuthorizationDetailsVerifier
    implements AuthorizationRequestExtensionVerifier {

  public boolean shouldVerify(OAuthRequestContext oAuthRequestContext) {
    return oAuthRequestContext.hasAuthorizationDetails()
        && oAuthRequestContext.authorizationDetails().hasVerifiableCredential();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    try {
      VerifiableCredentialVerifier verifier =
          new VerifiableCredentialVerifier(
              context.authorizationRequest().authorizationDetails(),
              context.serverConfiguration(),
              context.clientConfiguration());
      verifier.verify();
    } catch (AuthorizationDetailsInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          exception.error(), exception.errorDescription(), context);
    }
  }
}
