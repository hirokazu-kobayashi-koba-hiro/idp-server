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

package org.idp.server.basic.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import org.idp.server.platform.exception.UnSupportedException;

public class JsonWebEncrypterFactory {

  JsonWebKey jsonWebKey;

  public JsonWebEncrypterFactory(JsonWebKey jsonWebKey) {
    this.jsonWebKey = jsonWebKey;
  }

  public JWEEncrypter create() throws JsonWebKeyInvalidException, JOSEException {
    JsonWebKeyType jsonWebKeyType = jsonWebKey.keyType();
    switch (jsonWebKeyType) {
      case EC -> {
        return new ECDHEncrypter((ECPublicKey) jsonWebKey.toPublicKey());
      }
      case RSA -> {
        return new RSAEncrypter((RSAPublicKey) jsonWebKey.toPublicKey());
      }
      default -> {
        throw new UnSupportedException(
            String.format("unsupported encryption alg (%s)", jsonWebKeyType.name()));
      }
    }
  }
}
