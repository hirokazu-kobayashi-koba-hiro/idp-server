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

/**
 * JOSE (JSON Object Signing and Encryption) package.
 *
 * <p>Provides functionality for creating, verifying, and decrypting JWT/JWS/JWE/JWK.
 *
 * <h2>Main Entry Points</h2>
 *
 * <h3>Creation (Signing and Encryption)</h3>
 *
 * <ul>
 *   <li>{@link org.idp.server.platform.jose.JsonWebSignatureFactory} - Creates JWS (signed JWT)
 *   <li>{@link org.idp.server.platform.jose.NestedJsonWebEncryptionCreator} - Creates JWE
 *       (encrypted JWT) using Sign-then-Encrypt pattern
 * </ul>
 *
 * <h3>Verification and Decryption</h3>
 *
 * <ul>
 *   <li>{@link org.idp.server.platform.jose.JsonWebSignatureVerifier} - Verifies JWS signatures
 *   <li>{@link org.idp.server.platform.jose.JsonWebEncryptionDecrypter} - Decrypts JWE
 * </ul>
 *
 * <h3>Parsing (Context Creators)</h3>
 *
 * <ul>
 *   <li>{@link org.idp.server.platform.jose.JwsContextCreator} - Parses JWS strings
 *   <li>{@link org.idp.server.platform.jose.JweContextCreator} - Parses JWE strings
 *   <li>{@link org.idp.server.platform.jose.JwtContextCreator} - Parses unsigned JWT strings
 * </ul>
 *
 * <h2>Model Classes (Value Objects)</h2>
 *
 * <ul>
 *   <li>{@link org.idp.server.platform.jose.JsonWebSignature} - JWS value object
 *   <li>{@link org.idp.server.platform.jose.JsonWebEncryption} - JWE value object
 *   <li>{@link org.idp.server.platform.jose.JsonWebToken} - JWT value object
 *   <li>{@link org.idp.server.platform.jose.JsonWebKey} - Single JWK
 *   <li>{@link org.idp.server.platform.jose.JsonWebKeys} - JWK Set
 * </ul>
 *
 * <h2>Utilities (Internal Use)</h2>
 *
 * <ul>
 *   <li>{@link org.idp.server.platform.jose.SymmetricJweAlgorithms} - Symmetric JWE algorithm
 *       detection
 *   <li>{@link org.idp.server.platform.jose.JoseAlgorithmAnalyzer} - Algorithm type analysis
 *   <li>{@link org.idp.server.platform.jose.JsonWebEncrypterFactory} - Creates JWEEncrypter
 *       (internal)
 *   <li>{@link org.idp.server.platform.jose.JsonWebEncDecrypterFactory} - Creates JWEDecrypter
 *       (internal)
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Creating JWS</h3>
 *
 * <pre>{@code
 * JsonWebSignatureFactory factory = new JsonWebSignatureFactory(
 *     privateClaims, publicClaims, jwks, "RS256", "key-id");
 * JsonWebSignature jws = factory.create();
 * String token = jws.serialize();
 * }</pre>
 *
 * <h3>Creating JWE (Sign-then-Encrypt)</h3>
 *
 * <pre>{@code
 * NestedJsonWebEncryptionCreator creator = new NestedJsonWebEncryptionCreator(
 *     jws, "A256KW", "A256GCM", publicKeys, clientSecret);
 * String encryptedToken = creator.create();
 * }</pre>
 *
 * <h3>Decrypting JWE</h3>
 *
 * <pre>{@code
 * JsonWebEncryptionDecrypter decrypter = new JsonWebEncryptionDecrypter(
 *     encryptedToken, jwks, clientSecret);
 * JsonWebSignature innerJws = decrypter.decrypt();
 * }</pre>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7515">RFC 7515 - JWS</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC 7516 - JWE</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">RFC 7517 - JWK</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7518">RFC 7518 - JWA</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519 - JWT</a>
 */
package org.idp.server.platform.jose;
