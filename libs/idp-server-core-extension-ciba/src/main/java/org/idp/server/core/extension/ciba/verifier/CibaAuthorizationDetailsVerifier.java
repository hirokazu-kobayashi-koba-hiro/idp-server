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

package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetailsInvalidException;
import org.idp.server.core.openid.oauth.verifier.extension.AuthorizationDetailsVerifier;

/**
 * CibaAuthorizationDetailsVerifier
 *
 * <p>Validates authorization_details parameter in CIBA backchannel authentication requests.
 *
 * <p>RFC 9396 Section 4 - Authorization Request:
 *
 * <pre>
 * If the authorization server does not support authorization details in general
 * or the specific authorization details type, the value of the type parameter,
 * or other authorization details parameters, the authorization server returns
 * the error code invalid_authorization_details.
 *
 * The AS MUST refuse to process any unknown authorization details type or
 * authorization details not conforming to the respective type definition.
 * </pre>
 *
 * <p>This verifier ensures:
 *
 * <ul>
 *   <li>Each authorization detail contains required "type" field
 *   <li>All types are supported by the authorization server
 *   <li>All types are authorized for the client
 * </ul>
 *
 * @see org.idp.server.core.openid.oauth.verifier.extension.OAuthAuthorizationDetailsVerifier
 *     Similar implementation for OAuth/OIDC flows
 */
public class CibaAuthorizationDetailsVerifier implements CibaExtensionVerifier {

  @Override
  public boolean shouldNotVerify(CibaRequestContext context) {
    return !context.hasAuthorizationDetails();
  }

  @Override
  public void verify(CibaRequestContext context) {
    try {
      AuthorizationDetailsVerifier authorizationDetailsVerifier =
          new AuthorizationDetailsVerifier(
              context.authorizationDetails(),
              context.authorizationServerConfiguration(),
              context.clientConfiguration());
      authorizationDetailsVerifier.verify();
    } catch (AuthorizationDetailsInvalidException exception) {
      // Wrap OAuth exception to CIBA-specific exception
      throw new BackchannelAuthenticationBadRequestException(
          exception.error(), exception.errorDescription());
    }
  }
}
