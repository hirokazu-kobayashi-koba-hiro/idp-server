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

import java.util.List;
import java.util.Map;

public class RiskScore {

  double value;
  RiskLevel level;

  public RiskScore() {
    this.value = 0.0;
    this.level = RiskLevel.LOW;
  }

  public RiskScore(double value, RiskLevel level) {
    this.value = value;
    this.level = level;
  }

  public static RiskScore calculate(List<RiskSignalResult> results, RiskAssessmentConfig config) {
    if (results.isEmpty()) {
      return new RiskScore(0.0, RiskLevel.LOW);
    }

    double totalWeight = 0.0;
    double weightedScore = 0.0;

    for (RiskSignalResult result : results) {
      double weight = config.signalWeight(result.signalType());
      weightedScore += result.score() * weight;
      totalWeight += weight;
    }

    double normalizedScore = totalWeight > 0 ? weightedScore / totalWeight : 0.0;
    RiskLevel level = config.resolveLevel(normalizedScore);
    return new RiskScore(normalizedScore, level);
  }

  public double value() {
    return value;
  }

  public RiskLevel level() {
    return level;
  }

  public Map<String, Object> toMap() {
    return Map.of("risk_score", value, "risk_level", level.name());
  }
}
