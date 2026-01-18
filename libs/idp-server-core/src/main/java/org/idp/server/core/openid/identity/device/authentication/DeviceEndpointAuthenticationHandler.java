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

package org.idp.server.core.openid.identity.device.authentication;

import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.credential.repository.DeviceCredentialQueryRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.DeviceAuthenticationType;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;

/**
 * DeviceEndpointAuthenticationHandler handles device authentication for API endpoints.
 *
 * <p>This handler verifies device authentication based on the tenant's device authentication
 * policy. It supports multiple authentication types:
 *
 * <ul>
 *   <li>{@code none} - No authentication required
 *   <li>{@code access_token} - Bearer access token authentication
 *   <li>{@code device_secret_jwt} - Symmetric JWT authentication (HMAC)
 *   <li>{@code private_key_jwt} - Asymmetric JWT authentication (RSA/EC)
 * </ul>
 */
public class DeviceEndpointAuthenticationHandler {

  private final DeviceAccessTokenVerifier accessTokenVerifier;
  private final DeviceAuthenticationVerifier jwtVerifier;

  public DeviceEndpointAuthenticationHandler(
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      DeviceCredentialQueryRepository deviceCredentialQueryRepository) {
    this.accessTokenVerifier = new DeviceAccessTokenVerifier(oAuthTokenQueryRepository);
    this.jwtVerifier = new DeviceAuthenticationVerifier(deviceCredentialQueryRepository);
  }

  /**
   * Verifies device authentication based on tenant policy.
   *
   * @param tenant the tenant
   * @param deviceIdentifier the device identifier
   * @param authorizationHeader the Authorization header value (may be null)
   * @throws UnauthorizedException if authentication fails
   */
  public void verify(
      Tenant tenant, AuthenticationDeviceIdentifier deviceIdentifier, String authorizationHeader) {

    TenantIdentityPolicy identityPolicy = tenant.identityPolicyConfig();
    DeviceAuthenticationType authenticationType =
        identityPolicy.authenticationDeviceRule().authenticationType();

    if (authenticationType.isNone()) {
      return;
    }

    String credential = extractBearerCredential(authorizationHeader);

    if (authenticationType.isAccessToken()) {
      accessTokenVerifier.verify(tenant, deviceIdentifier, credential);
    } else if (authenticationType.isDeviceCredentialJwt()) {
      jwtVerifier.verify(tenant, deviceIdentifier, credential, authenticationType);
    } else {
      throw new UnauthorizedException(
          "Unsupported device authentication type: " + authenticationType);
    }
  }

  private String extractBearerCredential(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isEmpty()) {
      throw new UnauthorizedException("Authorization header is required for device authentication");
    }

    if (authorizationHeader.toLowerCase().startsWith("bearer ")) {
      return authorizationHeader.substring(7).trim();
    }

    throw new UnauthorizedException(
        "Invalid authorization header format. Expected: Bearer <token>");
  }
}
