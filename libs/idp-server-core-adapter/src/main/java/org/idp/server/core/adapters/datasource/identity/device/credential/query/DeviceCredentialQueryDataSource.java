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

package org.idp.server.core.adapters.datasource.identity.device.credential.query;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.core.adapters.datasource.identity.device.credential.ModelConverter;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredential;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentials;
import org.idp.server.core.openid.identity.device.credential.repository.DeviceCredentialQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class DeviceCredentialQueryDataSource implements DeviceCredentialQueryRepository {

  DeviceCredentialQuerySqlExecutor executor;

  public DeviceCredentialQueryDataSource(DeviceCredentialQuerySqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public Optional<DeviceCredential> findById(
      Tenant tenant, DeviceCredentialIdentifier credentialId) {
    Map<String, String> result = executor.selectById(tenant, credentialId);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConverter.convert(result));
  }

  @Override
  public DeviceCredentials findByDeviceId(Tenant tenant, AuthenticationDeviceIdentifier deviceId) {
    List<Map<String, String>> results = executor.selectByDeviceId(tenant, deviceId);

    if (results == null || results.isEmpty()) {
      return new DeviceCredentials();
    }

    List<DeviceCredential> list = results.stream().map(ModelConverter::convert).toList();
    return new DeviceCredentials(list);
  }

  @Override
  public Optional<DeviceCredential> findActiveByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId) {
    Map<String, String> result = executor.selectActiveByDeviceId(tenant, deviceId);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConverter.convert(result));
  }

  @Override
  public Optional<DeviceCredential> findActiveByDeviceIdAndAlgorithm(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String algorithm) {
    Map<String, String> result =
        executor.selectActiveByDeviceIdAndAlgorithm(tenant, deviceId, algorithm);

    if (result == null || result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(ModelConverter.convert(result));
  }
}
