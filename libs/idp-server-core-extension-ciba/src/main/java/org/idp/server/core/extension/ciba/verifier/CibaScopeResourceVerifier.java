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

import java.util.List;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.token.ResourceIndicatorResolver;

/**
 * Refuses a backchannel authentication request whose scopes belong to more than one resource.
 *
 * <p>RFC 9068 Section 3: if the values in the "scope" parameter refer to different default resource
 * indicator values, the authorization server SHOULD reject the request with "invalid_scope".
 *
 * <p>The backchannel authentication request is where a CIBA flow decides its scope, so it is the
 * counterpart of the authorization request for this flow. The error takes the form CIBA uses, which
 * is a response body rather than a redirect: there is no redirect URI to send one to.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9068.html#section-3">RFC 9068 Section 3</a>
 */
public class CibaScopeResourceVerifier implements CibaExtensionVerifier {

  /** Nothing to check on a deployment that has not mapped its scopes to resources. */
  @Override
  public boolean shouldVerify(CibaRequestContext context, ClientCredentials clientCredentials) {
    return !context.serverConfiguration().scopeResourceMapping().isEmpty();
  }

  @Override
  public void verify(CibaRequestContext context, ClientCredentials clientCredentials) {
    List<String> resources =
        ResourceIndicatorResolver.resolve(
            context.serverConfiguration().scopeResourceMapping(), context.scopes().toStringList());

    if (resources.size() > 1) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_scope",
          String.format(
              "backchannel authentication request scopes belong to different resources (%s)",
              resources));
    }
  }
}
