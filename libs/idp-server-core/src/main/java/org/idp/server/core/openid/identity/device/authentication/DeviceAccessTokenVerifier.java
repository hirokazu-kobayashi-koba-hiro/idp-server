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

import java.time.LocalDateTime;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * DeviceAccessTokenVerifier verifies device authentication using access tokens.
 *
 * <p>This verifier checks that the access token is valid and not expired.
 */
public class DeviceAccessTokenVerifier {

  private final OAuthTokenQueryRepository oAuthTokenQueryRepository;

  public DeviceAccessTokenVerifier(OAuthTokenQueryRepository oAuthTokenQueryRepository) {
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
  }

  /**
   * Verifies device authentication using access token.
   *
   * @param tenant the tenant
   * @param deviceIdentifier the device identifier
   * @param accessToken the access token value
   * @throws UnauthorizedException if verification fails
   */
  public void verify(
      Tenant tenant, AuthenticationDeviceIdentifier deviceIdentifier, String accessToken) {

    AccessTokenEntity accessTokenEntity = new AccessTokenEntity(accessToken);
    OAuthToken oAuthToken = oAuthTokenQueryRepository.find(tenant, accessTokenEntity);

    if (!oAuthToken.exists()) {
      throw new UnauthorizedException("Invalid access token");
    }

    if (oAuthToken.isExpiredAccessToken(LocalDateTime.now())) {
      throw new UnauthorizedException("Access token has expired");
    }

    if (!oAuthToken.user().hasAuthenticationDevice(deviceIdentifier)) {
      throw new UnauthorizedException(
          "Access token is not associated with this device: " + deviceIdentifier.value());
    }
  }
}
