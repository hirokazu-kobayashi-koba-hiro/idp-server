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

package org.idp.server.core.openid.oauth.verifier.extension;

import java.util.List;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.core.openid.token.ResourceIndicatorResolver;

/**
 * Refuses an authorization request whose scopes belong to more than one resource.
 *
 * <p>RFC 9068 Section 3: if the values in the "scope" parameter refer to different default resource
 * indicator values, the authorization server SHOULD reject the request with "invalid_scope".
 *
 * <p>The check runs at the authorization request because that is where the scope is decided. By the
 * time a token is requested the scope has already been granted, and refusing then would fail a
 * grant the client was told it had. It is also where the error can take the form this endpoint
 * requires, which is a redirect rather than a response body.
 *
 * <p>An access token can name only one resource, so such a request has no audience that satisfies
 * Section 2.2.3: every scope string a token carries has to have meaning for the resources its
 * audience names.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9068.html#section-3">RFC 9068 Section 3</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">invalid_scope</a>
 */
public class ScopeResourceVerifier implements AuthorizationRequestExtensionVerifier {

  /** Nothing to check on a deployment that has not mapped its scopes to resources. */
  @Override
  public boolean shouldVerify(OAuthRequestContext context) {
    return !context.serverConfiguration().scopeResourceMapping().isEmpty();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    List<String> resources =
        ResourceIndicatorResolver.resolve(
            context.serverConfiguration().scopeResourceMapping(), context.scopes().toStringList());

    if (resources.size() > 1) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_scope",
          String.format(
              "authorization request scopes belong to different resources (%s)", resources),
          context);
    }
  }
}
