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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.util.Base64;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Objects;

/** JsonWebKey */
public class JsonWebKey {
  JWK value;

  public JsonWebKey() {}

  public JsonWebKey(JWK value) {
    this.value = value;
  }

  JWK value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }

  public int size() {
    return value.size();
  }

  public String keyId() {
    return value.getKeyID();
  }

  public List<String> x5c() {
    List<Base64> x509CertChain = value.getX509CertChain();
    return x509CertChain.stream().map(Base64::toString).toList();
  }

  public boolean hasX5c() {
    List<Base64> x509CertChain = value.getX509CertChain();
    return Objects.nonNull(x509CertChain) && !x509CertChain.isEmpty();
  }

  public String algorithm() {
    if (value == null) {
      return "";
    }
    if (value.getAlgorithm() == null) {
      return "";
    }
    return value.getAlgorithm().getName();
  }

  public JsonWebKeyType keyType() {
    KeyType keyType = value.getKeyType();
    return JsonWebKeyType.of(keyType);
  }

  public PublicKey toPublicKey() throws JsonWebKeyInvalidException {
    JsonWebKeyType jsonWebKeyType = keyType();
    try {
      switch (jsonWebKeyType) {
        case RSA -> {
          return value.toRSAKey().toPublicKey();
        }
        case EC -> {
          return value.toECKey().toPublicKey();
        }
        case OCT -> {
          return value.toOctetKeyPair().toPublicKey();
        }
        default -> throw new JsonWebKeyInvalidException("unsupported key type");
      }
    } catch (JOSEException e) {
      throw new JsonWebKeyInvalidException(e.getMessage(), e);
    }
  }

  PrivateKey toPrivateKey() throws JsonWebKeyInvalidException {
    JsonWebKeyType jsonWebKeyType = keyType();
    try {
      switch (jsonWebKeyType) {
        case RSA -> {
          return value.toRSAKey().toPrivateKey();
        }
        case EC -> {
          return value.toECKey().toPrivateKey();
        }
        case OCT -> {
          return value.toOctetKeyPair().toPrivateKey();
        }
        default -> throw new JsonWebKeyInvalidException("unsupported key type");
      }
    } catch (JOSEException e) {
      throw new JsonWebKeyInvalidException(e.getMessage(), e);
    }
  }

  public int keySize() {
    return value.size();
  }
}
