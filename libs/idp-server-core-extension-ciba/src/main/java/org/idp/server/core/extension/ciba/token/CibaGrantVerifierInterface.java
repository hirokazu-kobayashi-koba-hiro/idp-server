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

package org.idp.server.core.extension.ciba.token;

import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.token.TokenRequestContext;

/**
 * CibaGrantVerifierInterface
 *
 * <p>Plugin interface for CIBA grant verification at the Token Endpoint. Implementations can
 * provide profile-specific verification logic (e.g., standard CIBA, FAPI-CIBA).
 *
 * <p>This interface enables extensible verification through the ServiceLoader mechanism, allowing
 * different verification strategies based on the CIBA profile.
 *
 * @see CibaGrantBaseVerifier
 * @see <a
 *     href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.11">CIBA
 *     Core Section 11 - Token Error Response</a>
 */
public interface CibaGrantVerifierInterface {

  /**
   * Returns the CIBA profile this verifier handles.
   *
   * @return the {@link CibaProfile} (e.g., CIBA, FAPI_CIBA)
   */
  CibaProfile profile();

  /**
   * Verifies the CIBA grant at the Token Endpoint.
   *
   * <p>Validates that the token request is authorized to exchange the auth_req_id for tokens.
   * Implementations should verify grant validity, client authorization, expiration, and
   * profile-specific requirements.
   *
   * @param context the token request context containing server and client configuration
   * @param request the original backchannel authentication request
   * @param cibaGrant the CIBA grant associated with the auth_req_id
   * @param clientCredentials the client credentials used for authentication
   * @throws org.idp.server.core.openid.token.exception.TokenBadRequestException if verification
   *     fails
   */
  void verify(
      TokenRequestContext context,
      BackchannelAuthenticationRequest request,
      CibaGrant cibaGrant,
      ClientCredentials clientCredentials);
}
