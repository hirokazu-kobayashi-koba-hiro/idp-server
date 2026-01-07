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

package org.idp.server.core.openid.identity.device.credential.repository;

import java.util.Optional;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredential;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentials;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface DeviceCredentialQueryRepository {

  Optional<DeviceCredential> findById(Tenant tenant, DeviceCredentialIdentifier credentialId);

  DeviceCredentials findByDeviceId(Tenant tenant, AuthenticationDeviceIdentifier deviceId);

  Optional<DeviceCredential> findActiveByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId);

  Optional<DeviceCredential> findActiveByDeviceIdAndAlgorithm(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String algorithm);
}
