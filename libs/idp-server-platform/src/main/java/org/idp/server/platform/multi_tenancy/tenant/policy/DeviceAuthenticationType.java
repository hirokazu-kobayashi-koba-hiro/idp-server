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

package org.idp.server.platform.multi_tenancy.tenant.policy;

/**
 * Device authentication type for authenticating device assertions.
 *
 * <p>Similar to OAuth client authentication types, this enum defines how a device authenticates
 * itself when making requests (e.g., JWT Bearer Grant with device-signed JWT).
 *
 * @see org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType
 */
public enum DeviceAuthenticationType {

  /** No authentication required. Device identity is not verified. */
  none,

  /**
   * Device authenticates using an OAuth 2.0 access token.
   *
   * <p>The device obtains an access token through standard OAuth flows (e.g., authorization code,
   * JWT Bearer Grant) and presents it in the Authorization header.
   */
  access_token,

  /**
   * Device authenticates using a JWT signed with a symmetric key (HMAC).
   *
   * <p>Similar to client_secret_jwt in OAuth. The device uses a shared secret to sign the JWT
   * assertion.
   */
  device_secret_jwt,

  /**
   * Device authenticates using a JWT signed with an asymmetric key (RSA/EC).
   *
   * <p>Similar to private_key_jwt in OAuth. The device uses its private key to sign the JWT
   * assertion, and the server verifies using the registered public key.
   */
  private_key_jwt;

  public boolean isNone() {
    return this == none;
  }

  public boolean isAccessToken() {
    return this == access_token;
  }

  public boolean isDeviceSecretJwt() {
    return this == device_secret_jwt;
  }

  public boolean isPrivateKeyJwt() {
    return this == private_key_jwt;
  }

  public boolean requiresCredential() {
    return this != none;
  }

  /**
   * Returns true if this type uses device credential JWT (symmetric or asymmetric).
   *
   * @return true if device credential JWT
   */
  public boolean isDeviceCredentialJwt() {
    return this == device_secret_jwt || this == private_key_jwt;
  }

  public boolean isSymmetric() {
    return this == device_secret_jwt;
  }

  public boolean isAsymmetric() {
    return this == private_key_jwt;
  }
}
