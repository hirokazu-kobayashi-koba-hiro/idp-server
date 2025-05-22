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


package org.idp.server.basic.type.oidc;

import java.util.Objects;

/**
 * id_token_hint OPTIONAL.
 *
 * <p>ID Token previously issued by the Authorization Server being passed as a hint about the
 * End-User's current or past authenticated session with the Client. If the End-User identified by
 * the ID Token is logged in or is logged in by the request, then the Authorization Server returns a
 * positive response; otherwise, it SHOULD return an error, such as login_required. When possible,
 * an id_token_hint SHOULD be present when prompt=none is used and an invalid_request error MAY be
 * returned if it is not; however, the server SHOULD respond successfully when possible, even if it
 * is not present. The Authorization Server need not be listed as an audience of the ID Token when
 * it is used as an id_token_hint value. If the ID Token received by the RP from the OP is
 * encrypted, to use it as an id_token_hint, the Client MUST decrypt the signed ID Token contained
 * within the encrypted ID Token. The Client MAY re-encrypt the signed ID token to the
 * Authentication Server using a key that enables the server to decrypt the ID Token, and use the
 * re-encrypted ID token as the id_token_hint value.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public class IdTokenHint {
  String value;

  public IdTokenHint() {}

  public IdTokenHint(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IdTokenHint that = (IdTokenHint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
