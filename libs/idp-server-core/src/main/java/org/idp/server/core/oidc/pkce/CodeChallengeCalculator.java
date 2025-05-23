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

package org.idp.server.core.oidc.pkce;

import org.idp.server.basic.base64.Base64Codeable;
import org.idp.server.basic.hash.MessageDigestable;
import org.idp.server.basic.type.pkce.CodeChallenge;
import org.idp.server.basic.type.pkce.CodeVerifier;

public class CodeChallengeCalculator implements MessageDigestable, Base64Codeable {

  CodeVerifier codeVerifier;

  public CodeChallengeCalculator(CodeVerifier codeVerifier) {
    this.codeVerifier = codeVerifier;
  }

  public CodeChallenge calculateWithPlain() {
    return new CodeChallenge(codeVerifier.value());
  }

  public CodeChallenge calculateWithS256() {
    byte[] bytes = digestWithSha256(codeVerifier.value());
    String encodedValue = encodeWithUrlSafe(bytes);
    return new CodeChallenge(encodedValue);
  }
}
