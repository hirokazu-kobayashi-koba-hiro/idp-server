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

package org.idp.server.core.openid.clientinstance.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.json.JsonConverter;

/**
 * The value that binds a registration to the key being registered.
 *
 * <pre>
 *   request_hash = base64url_nopad( SHA-256( challenge_bytes || canonical_jwk_utf8 ) )
 *   canonical_jwk = RFC 7638 thumbprint input (required members, lexicographic, no whitespace)
 * </pre>
 *
 * <p>It is the {@code nonce} of the ID token that authenticates the registration. Because it covers
 * the key and not only the challenge, a stolen ID token only authenticates the registration of the
 * key it was obtained for.
 */
public class ClientInstanceRequestHash {

  static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  String value;

  ClientInstanceRequestHash(String value) {
    this.value = value;
  }

  /**
   * Derives the request hash of a challenge and an instance key.
   *
   * @throws ClientInstanceRegistrationException when the challenge is not base64url or the key is
   *     not a valid JWK
   */
  public static ClientInstanceRequestHash derive(
      String challenge, Map<String, Object> instanceKey) {
    try {
      JsonWebKey jsonWebKey = JwkParser.parse(jsonConverter.write(instanceKey));
      byte[] challengeBytes = Base64.getUrlDecoder().decode(challenge);
      byte[] canonical = jsonWebKey.canonicalJson().getBytes(StandardCharsets.UTF_8);

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(challengeBytes);
      digest.update(canonical);

      return new ClientInstanceRequestHash(
          Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest()));
    } catch (JsonWebKeyInvalidException e) {
      throw new ClientInstanceRegistrationException(
          "client_instance_public_key is not a valid JWK: " + e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ClientInstanceRegistrationException("challenge is not base64url encoded");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public String value() {
    return value;
  }

  /** Constant time comparison with a presented value. */
  public boolean matches(String presented) {
    if (presented == null || presented.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        value.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
  }
}
