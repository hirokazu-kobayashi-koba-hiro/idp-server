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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class RiskAssessmentResult {

  RiskScore riskScore;
  List<RiskSignalResult> signalResults;

  public RiskAssessmentResult() {
    this.riskScore = new RiskScore();
    this.signalResults = List.of();
  }

  public RiskAssessmentResult(RiskScore riskScore, List<RiskSignalResult> signalResults) {
    this.riskScore = riskScore;
    this.signalResults = signalResults;
  }

  public RiskScore riskScore() {
    return riskScore;
  }

  public RiskLevel riskLevel() {
    return riskScore.level();
  }

  public List<RiskSignalResult> signalResults() {
    return signalResults;
  }

  public DefaultSecurityEventType securityEventType() {
    return switch (riskScore.level()) {
      case HIGH -> DefaultSecurityEventType.risk_assessment_high;
      case MEDIUM -> DefaultSecurityEventType.risk_assessment_medium;
      case LOW -> DefaultSecurityEventType.risk_assessment_low;
    };
  }

  public boolean exists() {
    return riskScore != null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("risk_score", riskScore.value());
    map.put("risk_level", riskScore.level().name());
    map.put(
        "signals",
        signalResults.stream().map(RiskSignalResult::toMap).collect(Collectors.toList()));
    return map;
  }
}
