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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

/** JwkParser */
public class JwkParser {

  public static JsonWebKey parse(String value) throws JsonWebKeyInvalidException {
    try {
      JWK jwk = JWK.parse(value);
      return new JsonWebKey(jwk);
    } catch (ParseException e) {
      throw new JsonWebKeyInvalidException(e.getMessage(), e);
    }
  }

  /**
   * The public key of {@code certificate}, labelled with {@code algorithm}.
   *
   * <p>For trust sources that carry a certificate rather than a JWKS ({@code x5c}). The caller has
   * already decided the certificate is trustworthy; this only changes the encoding, so that the
   * JOSE layer verifies signatures the same way whatever the key came from.
   *
   * <p>A certificate says nothing about which JWS algorithm its key signs with, and the key
   * selection this feeds matches on {@code alg} when the header carries no {@code kid} — which an
   * attester publishing through {@code x5c} has no reason to send. Without the label the key is
   * never found and the request fails as "no trusted key", which is the wrong answer and an opaque
   * one.
   *
   * <p>Taking the label from the presented header is safe because it selects nothing but the
   * verifier: the key material is whatever the validated certificate holds, so a header claiming an
   * algorithm that key cannot produce fails at signature verification rather than earlier.
   */
  public static JsonWebKey parseFromCertificate(X509Certificate certificate, String algorithm)
      throws JsonWebKeyInvalidException {
    try {
      JWK jwk = JWK.parse(certificate);
      JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(algorithm);

      if (jwk instanceof ECKey ecKey) {
        return new JsonWebKey(new ECKey.Builder(ecKey).algorithm(jwsAlgorithm).build());
      }
      if (jwk instanceof RSAKey rsaKey) {
        return new JsonWebKey(new RSAKey.Builder(rsaKey).algorithm(jwsAlgorithm).build());
      }
      throw new JsonWebKeyInvalidException(
          "unsupported certificate key type for signature verification: " + jwk.getKeyType());
    } catch (JOSEException e) {
      throw new JsonWebKeyInvalidException(
          "failed to read the public key of the certificate: " + e.getMessage(), e);
    }
  }

  public static JsonWebKeys parseKeys(String value) throws JsonWebKeyInvalidException {
    if (value == null || value.trim().isEmpty()) {
      throw new JsonWebKeyInvalidException(
          "JWKS value is null or empty. Client must have a valid JWKS for signed request object verification.");
    }
    try {
      JWKSet jwkSet = JWKSet.parse(value);
      List<JWK> keys = jwkSet.getKeys();
      List<JsonWebKey> jsonWebKeys = keys.stream().map(JsonWebKey::new).toList();
      return new JsonWebKeys(jsonWebKeys);
    } catch (ParseException e) {
      throw new JsonWebKeyInvalidException(e.getMessage(), e);
    }
  }

  public static Map<String, Object> parsePublicKeys(String value)
      throws JsonWebKeyInvalidException {
    if (value == null || value.trim().isEmpty()) {
      throw new JsonWebKeyInvalidException(
          "JWKS value is null or empty. Client must have a valid JWKS.");
    }
    try {
      JWKSet jwkSet = JWKSet.parse(value);
      JWKSet publicJWKSet = jwkSet.toPublicJWKSet();
      return publicJWKSet.toJSONObject();
    } catch (ParseException e) {
      throw new JsonWebKeyInvalidException(e.getMessage(), e);
    }
  }
}
