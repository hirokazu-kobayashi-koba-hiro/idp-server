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

import java.util.HashMap;
import java.util.Map;

/**
 * JoseHandler
 *
 * <p>Facade for parsing JOSE tokens. Automatically detects the token type (JWT, JWS, or JWE) and
 * delegates to the appropriate context creator.
 *
 * <p>Token type detection is based on the {@code alg} parameter in the JOSE header:
 *
 * <ul>
 *   <li>{@code alg: "none"} → Plain JWT (unsigned)
 *   <li>{@code alg} is JWSAlgorithm (RS256, ES256, etc.) → JWS (signed)
 *   <li>{@code alg} is JWEAlgorithm (RSA-OAEP, A256KW, etc.) → JWE (encrypted)
 * </ul>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * JoseHandler handler = new JoseHandler();
 *
 * // Parse any JOSE token - type is auto-detected
 * JoseContext context = handler.handle(token, publicJwks, privateJwks, clientSecret);
 *
 * // Access parsed claims
 * JsonWebTokenClaims claims = context.claims();
 * }</pre>
 *
 * @see JoseType
 * @see JoseContext
 */
public class JoseHandler {

  Map<JoseType, JoseContextCreator> creators;

  /** Creates a new JoseHandler with all supported token type handlers. */
  public JoseHandler() {
    creators = new HashMap<>();
    creators.put(JoseType.plain, new JwtContextCreator());
    creators.put(JoseType.signature, new JwsContextCreator());
    creators.put(JoseType.encryption, new JweContextCreator());
  }

  /**
   * Parses a JOSE token and returns its context.
   *
   * <p>The token type is automatically detected, and the appropriate handler is invoked:
   *
   * <ul>
   *   <li>Plain JWT: No verification, just parses claims
   *   <li>JWS: Parses and prepares for signature verification
   *   <li>JWE: Decrypts using privateJwks (asymmetric) or secret (symmetric)
   * </ul>
   *
   * @param jose the JOSE token string (JWT, JWS, or JWE)
   * @param publicJwks public keys in JWKS format (for signature verification)
   * @param privateJwks private keys in JWKS format (for JWE decryption with asymmetric algorithms)
   * @param secret client secret (for JWE decryption with symmetric algorithms like A256KW)
   * @return the parsed context containing claims and metadata
   * @throws JoseInvalidException if parsing or decryption fails
   */
  public JoseContext handle(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {
    JoseType joseType = JoseType.parse(jose);
    JoseContextCreator joseContextCreator = creators.get(joseType);
    return joseContextCreator.create(jose, publicJwks, privateJwks, secret);
  }
}
