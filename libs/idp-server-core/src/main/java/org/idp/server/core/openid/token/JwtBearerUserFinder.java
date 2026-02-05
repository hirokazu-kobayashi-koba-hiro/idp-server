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

package org.idp.server.core.openid.token;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Implementation of JwtBearerUserFindingDelegate that looks up users from the repository.
 *
 * <p>This class provides user lookup for JWT Bearer Grant processing. It supports different claim
 * mappings to find users by various attributes.
 *
 * @see JwtBearerUserFindingDelegate
 */
public class JwtBearerUserFinder implements JwtBearerUserFindingDelegate {

  private final UserQueryRepository userQueryRepository;

  public JwtBearerUserFinder(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  @Override
  public User findUser(
      Tenant tenant, String providerId, String subject, String subjectClaimMapping) {
    // For device-issued JWTs, use the claim mapping
    if (providerId != null && providerId.startsWith("device")) {
      return findByDeviceClaimMapping(tenant, subject, subjectClaimMapping);
    }

    // For external IdP JWTs, use the claim mapping
    return findByClaimMapping(tenant, providerId, subject, subjectClaimMapping);
  }

  private User findByDeviceClaimMapping(Tenant tenant, String subject, String subjectClaimMapping) {
    if (subject == null || subject.isEmpty()) {
      return User.notFound();
    }

    switch (subjectClaimMapping) {
      case "sub":
        // Treat subject as user ID
        return userQueryRepository.findById(tenant, new UserIdentifier(subject));
      case "device_id":
      default:
        // Default for device: lookup user by authentication device ID
        // This is more secure as the device can only authenticate as its registered owner
        return userQueryRepository.findByAuthenticationDevice(tenant, subject);
    }
  }

  private User findByClaimMapping(
      Tenant tenant, String providerId, String subject, String subjectClaimMapping) {
    if (subject == null || subject.isEmpty()) {
      return User.notFound();
    }

    switch (subjectClaimMapping) {
      case "sub":
        // Direct user ID lookup
        return userQueryRepository.findById(tenant, new UserIdentifier(subject));
      case "email":
        // Lookup by email
        return userQueryRepository.findByEmail(tenant, subject, providerId);
      default:
        // Default to external user ID lookup
        return userQueryRepository.findByExternalIdpSubject(tenant, subject, providerId);
    }
  }

  @Override
  public AuthenticationDevice findAuthenticationDevice(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId) {
    // Find user by device ID
    User user = userQueryRepository.findByDeviceId(tenant, deviceId, "idp-server");
    if (!user.exists()) {
      return new AuthenticationDevice();
    }
    // Extract the authentication device from the user
    return user.findAuthenticationDevice(deviceId.value());
  }
}
