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

package org.idp.server.core.openid.identity.device.credential;

/**
 * Device credential types.
 *
 * <ul>
 *   <li>{@code jwt_bearer_symmetric} - HMAC keys for JWT Bearer Grant (RFC 7523). IdP manages
 *       secret.
 *   <li>{@code jwt_bearer_asymmetric} - RSA/EC public keys for JWT Bearer Grant. IdP manages public
 *       key.
 *   <li>{@code fido2} - Reference to FIDO2/WebAuthn credential in FIDO server.
 *   <li>{@code fido_uaf} - Reference to FIDO UAF credential in FIDO server.
 * </ul>
 */
public enum DeviceCredentialType {
  jwt_bearer_symmetric,
  jwt_bearer_asymmetric,
  fido2,
  fido_uaf;

  /** Returns true if this is a symmetric key credential (jwt_bearer_symmetric). */
  public boolean isSymmetric() {
    return this == jwt_bearer_symmetric;
  }

  /** Returns true if this is an asymmetric key credential (jwt_bearer_asymmetric). */
  public boolean isAsymmetric() {
    return this == jwt_bearer_asymmetric;
  }

  /** Returns true if this is a JWT Bearer credential (symmetric or asymmetric). */
  public boolean isJwtBearer() {
    return this == jwt_bearer_symmetric || this == jwt_bearer_asymmetric;
  }

  /** Returns true if this is a FIDO credential (fido2 or fido_uaf). */
  public boolean isFido() {
    return this == fido2 || this == fido_uaf;
  }

  /** Returns true if this is a FIDO2/WebAuthn credential. */
  public boolean isFido2() {
    return this == fido2;
  }

  /** Returns true if this is a FIDO UAF credential. */
  public boolean isFidoUaf() {
    return this == fido_uaf;
  }
}
