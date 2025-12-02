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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.plugin.CibaVerifierPluginLoader;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.platform.exception.UnSupportedException;

/**
 * CibaRequestVerifier
 *
 * <p>CIBA request verification following the same architecture as OAuthRequestVerifier.
 *
 * <p>Structure: - Base Verifiers: Profile-specific verification (CIBA, FAPI_CIBA) - Extension
 * Verifiers: Common verifications across all profiles (RequestObject, AuthorizationDetails)
 *
 * <p>RFC 9396 Section 4: AS MUST refuse to process any unknown authorization details type
 */
public class CibaRequestVerifier {

  Map<CibaProfile, CibaVerifier> baseVerifiers;
  List<CibaExtensionVerifier> extensionVerifiers;

  public CibaRequestVerifier() {
    this.baseVerifiers = new HashMap<>();
    this.baseVerifiers.put(CibaProfile.CIBA, new CibaRequestNormalProfileVerifier());
    Map<CibaProfile, CibaVerifier> loadedVerifiers = CibaVerifierPluginLoader.load();
    this.baseVerifiers.putAll(loadedVerifiers);

    // Extension Verifiers: Common across all CIBA profiles
    this.extensionVerifiers = new ArrayList<>();
    this.extensionVerifiers.add(new CibaRequestObjectVerifier());
    this.extensionVerifiers.add(new CibaRequiredRarVerifier());
    this.extensionVerifiers.add(new CibaAuthorizationDetailsVerifier());
  }

  public void verify(CibaRequestContext context, ClientCredentials clientCredentials) {
    // 1. Profile-specific base verification
    CibaVerifier cibaVerifier = baseVerifiers.get(context.profile());
    if (Objects.isNull(cibaVerifier)) {
      throw new UnSupportedException(
          String.format("unsupported ciba profile (%s)", context.profile().name()));
    }
    cibaVerifier.verify(context, clientCredentials);

    // 2. Common extension verifications
    extensionVerifiers.forEach(
        extensionVerifier -> {
          if (!extensionVerifier.shouldVerify(context, clientCredentials)) {
            return;
          }
          extensionVerifier.verify(context, clientCredentials);
        });
  }
}
