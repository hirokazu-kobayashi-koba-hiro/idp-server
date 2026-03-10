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
package org.idp.server.core.openid.authentication.risk.signal;

import org.idp.server.core.openid.authentication.risk.DeviceFingerprint;
import org.idp.server.core.openid.authentication.risk.RiskSignalResult;
import org.idp.server.core.openid.authentication.risk.repository.UserKnownDeviceQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.RiskAssessmentConfig;
import org.idp.server.platform.multi_tenancy.tenant.policy.RiskSignalType;
import org.idp.server.platform.security.type.DeviceInfo;
import org.idp.server.platform.type.RequestAttributes;

public class NewDeviceSignalEvaluator implements RiskSignalEvaluator {

  UserKnownDeviceQueryRepository userKnownDeviceQueryRepository;

  public NewDeviceSignalEvaluator(UserKnownDeviceQueryRepository userKnownDeviceQueryRepository) {
    this.userKnownDeviceQueryRepository = userKnownDeviceQueryRepository;
  }

  @Override
  public RiskSignalType signalType() {
    return RiskSignalType.NEW_DEVICE;
  }

  @Override
  public RiskSignalResult evaluate(
      Tenant tenant, User user, RequestAttributes requestAttributes, RiskAssessmentConfig config) {
    DeviceInfo deviceInfo = DeviceInfo.parse(requestAttributes.getUserAgent().value());
    DeviceFingerprint fingerprint = DeviceFingerprint.from(deviceInfo);

    boolean isKnown = userKnownDeviceQueryRepository.exists(tenant, user, fingerprint);

    if (isKnown) {
      return new RiskSignalResult(RiskSignalType.NEW_DEVICE, 0.0, "known device");
    }
    return new RiskSignalResult(RiskSignalType.NEW_DEVICE, 1.0, "new device detected");
  }
}
