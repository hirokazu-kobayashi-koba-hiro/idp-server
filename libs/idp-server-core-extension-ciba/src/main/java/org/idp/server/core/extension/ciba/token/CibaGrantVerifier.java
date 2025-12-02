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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.plugin.CibaGrantVerifierPluginLoader;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.platform.exception.UnSupportedException;

public class CibaGrantVerifier {

  Map<CibaProfile, CibaGrantVerifierInterface> baseVerifiers;

  public CibaGrantVerifier() {
    this.baseVerifiers = new HashMap<>();
    baseVerifiers.put(CibaProfile.CIBA, new CibaGrantBaseVerifier());
    Map<CibaProfile, CibaGrantVerifierInterface> loadedBaseVerifiers =
        CibaGrantVerifierPluginLoader.load();
    baseVerifiers.putAll(loadedBaseVerifiers);
  }

  public void verify(
      TokenRequestContext context,
      BackchannelAuthenticationRequest request,
      CibaGrant cibaGrant,
      ClientCredentials clientCredentials) {
    CibaGrantVerifierInterface baseVerifier = baseVerifiers.get(request.profile());

    if (Objects.isNull(baseVerifier)) {
      throw new UnSupportedException(
          String.format("idp server does not supported profile (%s)", request.profile().name()));
    }
    baseVerifier.verify(context, request, cibaGrant, clientCredentials);
  }
}
