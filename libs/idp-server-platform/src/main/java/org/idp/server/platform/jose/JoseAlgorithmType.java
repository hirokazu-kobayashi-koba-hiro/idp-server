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

/**
 * JoseAlgorithmType
 *
 * <p>Represents the algorithm type of a JOSE token.
 */
public enum JoseAlgorithmType {

  /** Unsigned JWT (alg: none) */
  UNSIGNED,

  /** Signed JWT (JWS) with symmetric or asymmetric signature */
  SIGNATURE,

  /** Encrypted JWT (JWE) with symmetric key management algorithm */
  SYMMETRIC_ENCRYPTION,

  /** Encrypted JWT (JWE) with asymmetric key management algorithm */
  ASYMMETRIC_ENCRYPTION;

  public boolean isUnsigned() {
    return this == UNSIGNED;
  }

  public boolean isSignature() {
    return this == SIGNATURE;
  }

  public boolean isSymmetricEncryption() {
    return this == SYMMETRIC_ENCRYPTION;
  }

  public boolean isAsymmetricEncryption() {
    return this == ASYMMETRIC_ENCRYPTION;
  }

  public boolean isEncryption() {
    return this == SYMMETRIC_ENCRYPTION || this == ASYMMETRIC_ENCRYPTION;
  }
}
