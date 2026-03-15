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
package org.idp.server.core.openid.authentication.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.idp.server.core.openid.authentication.risk.repository.UserKnownDeviceCommandRepository;
import org.idp.server.core.openid.authentication.risk.signal.RiskSignalEvaluator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.RiskAssessmentConfig;
import org.idp.server.platform.security.type.DeviceInfo;
import org.idp.server.platform.type.RequestAttributes;

public class RiskAssessmentService {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(RiskAssessmentService.class);

  List<RiskSignalEvaluator> evaluators;
  UserKnownDeviceCommandRepository knownDeviceCommandRepository;

  public RiskAssessmentService(
      List<RiskSignalEvaluator> evaluators,
      UserKnownDeviceCommandRepository knownDeviceCommandRepository) {
    this.evaluators = evaluators;
    this.knownDeviceCommandRepository = knownDeviceCommandRepository;
  }

  /**
   * Assesses risk if enabled for the tenant. Returns empty if risk assessment is disabled.
   * Configuration is read from tenant's identity_policy_config.risk_assessment.
   *
   * @return Optional containing the result if risk assessment is enabled, empty otherwise
   */
  public Optional<RiskAssessmentResult> assessIfEnabled(
      Tenant tenant, User user, RequestAttributes requestAttributes) {
    RiskAssessmentConfig config = resolveConfig(tenant);
    if (!config.isEnabled()) {
      return Optional.empty();
    }
    return Optional.of(assess(tenant, user, requestAttributes, config));
  }

  /**
   * Records or updates a known device entry for the authenticated user. Called after successful
   * authentication to enable future NewDevice risk signal evaluation.
   */
  public void recordDevice(Tenant tenant, User user, RequestAttributes requestAttributes) {
    RiskAssessmentConfig config = resolveConfig(tenant);
    if (!config.isEnabled()) {
      return;
    }
    try {
      DeviceInfo deviceInfo = DeviceInfo.parse(requestAttributes.getUserAgent().value());
      DeviceFingerprint fingerprint = DeviceFingerprint.from(deviceInfo);
      UserKnownDevice knownDevice =
          new UserKnownDevice(
              tenant.identifierValue(),
              user.sub(),
              fingerprint,
              deviceInfo.os(),
              deviceInfo.browser(),
              deviceInfo.platform(),
              requestAttributes.getIpAddress().value(),
              0.0,
              0.0,
              "",
              "",
              1,
              null,
              null);
      knownDeviceCommandRepository.upsert(tenant, knownDevice);
    } catch (Exception e) {
      log.warn("Failed to upsert known device: {}", e.getMessage());
    }
  }

  private RiskAssessmentConfig resolveConfig(Tenant tenant) {
    RiskAssessmentConfig config = tenant.riskAssessmentConfig();
    if (config == null) {
      return new RiskAssessmentConfig();
    }
    return config;
  }

  RiskAssessmentResult assess(
      Tenant tenant, User user, RequestAttributes requestAttributes, RiskAssessmentConfig config) {
    List<RiskSignalResult> results = new ArrayList<>();
    for (RiskSignalEvaluator evaluator : evaluators) {
      if (config.isSignalEnabled(evaluator.signalType())) {
        results.add(evaluator.evaluate(tenant, user, requestAttributes, config));
      }
    }
    RiskScore score = RiskScore.calculate(results, config);
    return new RiskAssessmentResult(score, results);
  }
}
