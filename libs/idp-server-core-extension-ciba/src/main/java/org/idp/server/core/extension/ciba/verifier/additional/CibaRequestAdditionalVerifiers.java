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

package org.idp.server.core.extension.ciba.verifier.additional;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.plugin.CibaRequestAdditionalVerifierPluginLoader;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.authentication.PasswordVerificationDelegation;

public class CibaRequestAdditionalVerifiers {

  List<CibaRequestAdditionalVerifier> additionalVerifiers;

  public CibaRequestAdditionalVerifiers(
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.additionalVerifiers = new ArrayList<>();
    additionalVerifiers.add(new UserResolvedVerifier());
    additionalVerifiers.add(new UserCodeAsPasswordVerifier(passwordVerificationDelegation));
    additionalVerifiers.add(new IdentityVerificationVerifier());
    List<CibaRequestAdditionalVerifier> extensionVerifiers =
        CibaRequestAdditionalVerifierPluginLoader.load();
    additionalVerifiers.addAll(extensionVerifiers);
  }

  public void verify(CibaRequestContext cibaRequestContext, User user) {

    for (CibaRequestAdditionalVerifier verifier : additionalVerifiers) {

      if (verifier.shouldVerify(cibaRequestContext, user)) {
        verifier.verify(cibaRequestContext, user);
      }
    }
  }
}
