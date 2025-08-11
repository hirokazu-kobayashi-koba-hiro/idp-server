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

package org.idp.server.core.extension.verifiable_credentials.verifier;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetailsInvalidException;
import org.idp.server.core.openid.oauth.type.vc.VerifiableCredentialInvalidException;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.core.openid.oauth.verifier.extension.VerifiableCredentialVerifier;

public class OAuthVerifiableCredentialVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldVerify(OAuthRequestContext oAuthRequestContext) {
    return oAuthRequestContext.hasAuthorizationDetails()
        && oAuthRequestContext.authorizationDetails().hasVerifiableCredential();
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
