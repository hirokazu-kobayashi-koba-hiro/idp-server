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


package org.idp.server.core.oidc.clientcredentials;

import java.util.Objects;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebTokenClaims;

public class ClientAssertionJwt {
  JsonWebSignature jsonWebSignature;

  public ClientAssertionJwt() {}

  public ClientAssertionJwt(JsonWebSignature jsonWebSignature) {
    this.jsonWebSignature = jsonWebSignature;
  }

  public JsonWebTokenClaims claims() {
    return jsonWebSignature.claims();
  }

  public String subject() {
    return claims().getSub();
  }

  public String iss() {
    return claims().getIss();
  }

  public boolean exists() {
    return Objects.nonNull(jsonWebSignature) && jsonWebSignature.exists();
  }
}
