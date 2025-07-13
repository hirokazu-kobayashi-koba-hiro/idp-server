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

package org.idp.server.platform.jose;

import java.security.PublicKey;
import org.junit.jupiter.api.Test;

public class JsonWebKeyTest {

  @Test
  void toPublicKeyWIthES256() throws JsonWebKeyInvalidException {
    String jwkValue =
        """
                {
                    "kty": "EC",
                    "use": "sig",
                    "crv": "P-256",
                    "kid": "test",
                    "x": "Ns2wk-WP3peB5yCHdsNkDJG5bGC2oRkwLrfNY5OpAMk",
                    "y": "G-J8VQBHkvCV6Eqx_niTH4OoMUFNqZFiEureoMGg40o",
                    "alg": "ES256"
                }
                """;
    JsonWebKey jsonWebKey = JwkParser.parse(jwkValue);
    PublicKey publicKey = jsonWebKey.toPublicKey();
    System.out.println(publicKey);
  }
}
