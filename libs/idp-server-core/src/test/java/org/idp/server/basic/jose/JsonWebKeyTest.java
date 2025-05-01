package org.idp.server.basic.jose;

import java.security.PublicKey;
import org.idp.server.basic.jose.JsonWebKey;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JwkParser;
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
