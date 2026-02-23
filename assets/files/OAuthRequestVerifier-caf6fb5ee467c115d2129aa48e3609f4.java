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

package org.idp.server.core.openid.oauth.verifier;

import java.util.*;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.verifier.extension.JarmVerifier;
import org.idp.server.core.openid.oauth.verifier.extension.OAuthAuthorizationDetailsVerifier;
import org.idp.server.core.openid.oauth.verifier.extension.PushedAuthorizationRequestVerifier;
import org.idp.server.core.openid.oauth.verifier.extension.RequestObjectVerifier;
import org.idp.server.core.openid.plugin.request.AuthorizationRequestExtensionVerifierPluginLoader;
import org.idp.server.core.openid.plugin.request.AuthorizationRequestVerifierPluginLoader;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;

/** OAuthRequestVerifier */
public class OAuthRequestVerifier {

  Map<AuthorizationProfile, AuthorizationRequestVerifier> baseVerifiers = new HashMap<>();
  List<AuthorizationRequestExtensionVerifier> extensionVerifiers = new ArrayList<>();
  LoggerWrapper log = LoggerWrapper.getLogger(OAuthRequestVerifier.class);

  public OAuthRequestVerifier() {
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new OAuth2RequestVerifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new OidcRequestVerifier());
    Map<AuthorizationProfile, AuthorizationRequestVerifier> loadedVerifiers =
        AuthorizationRequestVerifierPluginLoader.load();
    baseVerifiers.putAll(loadedVerifiers);

    List<AuthorizationRequestExtensionVerifier> loadedExtensionVerifiers =
        AuthorizationRequestExtensionVerifierPluginLoader.load();
    extensionVerifiers.addAll(loadedExtensionVerifiers);
    extensionVerifiers.add(new PushedAuthorizationRequestVerifier());
    extensionVerifiers.add(new RequestObjectVerifier());
    extensionVerifiers.add(new OAuthAuthorizationDetailsVerifier());
    extensionVerifiers.add(new JarmVerifier());
  }

  public void verify(OAuthRequestContext context) {
    AuthorizationRequestVerifier baseRequestVerifier = baseVerifiers.get(context.profile());
    if (Objects.isNull(baseRequestVerifier)) {
      throw new UnSupportedException(
          String.format("idp server unsupported profile (%s)", context.profile().name()));
    }
    baseRequestVerifier.verify(context);
    extensionVerifiers.forEach(
        verifier -> {
          if (verifier.shouldVerify(context)) {
            verifier.verify(context);
          }
        });
  }
}
