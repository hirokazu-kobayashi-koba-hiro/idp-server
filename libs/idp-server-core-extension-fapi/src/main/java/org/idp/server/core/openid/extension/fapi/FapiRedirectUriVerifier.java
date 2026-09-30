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

package org.idp.server.core.openid.extension.fapi;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.verifier.base.OAuthRequestBaseVerifier;

/**
 * The redirect_uri requirements of FAPI 1.0 Baseline 5.2.2-8/9/10, which FAPI 1.0 Advanced carries
 * over (Part 2 Section 5.2.2) and FAPI 2.0 requires of pushed authorization requests (Section
 * 5.3.2.2-6, on top of OAuth 2.0's registration).
 *
 * <p>These are checked before anything that can report an error by redirecting: until the
 * redirect_uri is known to be registered, it is not a place to send anything to. All failures are
 * {@link OAuthBadRequestException}, shown by the authorization server rather than redirected.
 *
 * <p>The same checks apply whether or not the request is an OpenID Connect request. OIDC requires
 * them of its own (Core 3.1.2.1), but a request without the openid scope would otherwise pass with
 * only the response_type and scope checks of {@link OAuthRequestBaseVerifier} (Issue #1902).
 */
public class FapiRedirectUriVerifier {

  String profileName;
  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();

  /**
   * @param profileName the profile named in error descriptions, such as "FAPI Baseline profile"
   */
  public FapiRedirectUriVerifier(String profileName) {
    this.profileName = profileName;
  }

  public void verify(OAuthRequestContext context) {
    throwExceptionIfUnregisteredRedirectUri(context);
    throwExceptionIfNotContainsRedirectUri(context);
    oAuthRequestBaseVerifier.throwExceptionIfRedirectUriContainsFragment(context);
    throwExceptionIfUnMatchRedirectUri(context);
  }

  /** shall require redirect URIs to be pre-registered; */
  void throwExceptionIfUnregisteredRedirectUri(OAuthRequestContext context) {
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.hasRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format("When %s, shall require redirect URIs to be pre-registered", profileName),
          context.tenant());
    }
  }

  /** shall require the redirect_uri in the authorization request; */
  void throwExceptionIfNotContainsRedirectUri(OAuthRequestContext context) {
    if (!context.hasRedirectUriInRequest()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When %s, shall require the redirect_uri in the authorization request", profileName),
          context.tenant());
    }
  }

  /**
   * shall require the value of redirect_uri to exactly match one of the pre-registered redirect
   * URIs;
   */
  void throwExceptionIfUnMatchRedirectUri(OAuthRequestContext context) {
    if (!context.isRegisteredRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When %s, shall require the value of redirect_uri to exactly match one of the pre-registered redirect URIs (%s)",
              profileName, context.redirectUri().value()),
          context.tenant());
    }
  }
}
