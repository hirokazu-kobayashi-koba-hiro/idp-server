/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.jose;

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
