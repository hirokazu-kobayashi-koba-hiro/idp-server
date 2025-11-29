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

import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.CibaRequestContext;

/**
 * CibaRequestNormalProfileVerifier
 *
 * <p>Normal CIBA profile base verification.
 *
 * <p>Note: Extension verifications (RequestObject, AuthorizationDetails) are now handled by
 * CibaRequestVerifier to follow the same architecture as OAuthRequestVerifier.
 */
public class CibaRequestNormalProfileVerifier implements CibaVerifier {

  CibaRequestBaseVerifier baseVerifier;

  public CibaRequestNormalProfileVerifier() {
    this.baseVerifier = new CibaRequestBaseVerifier();
  }

  @Override
  public CibaProfile profile() {
    return CibaProfile.CIBA;
  }

  @Override
  public void verify(CibaRequestContext context) {
    baseVerifier.verify(context);
  }
}
