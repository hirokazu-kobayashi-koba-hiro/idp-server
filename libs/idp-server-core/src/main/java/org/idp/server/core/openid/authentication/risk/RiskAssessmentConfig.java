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
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class RiskAssessmentConfig implements JsonReadable {

  boolean enabled;
  Map<String, SignalConfig> signals;
  ThresholdConfig thresholds;

  public RiskAssessmentConfig() {
    this.enabled = false;
    this.signals = new HashMap<>();
    this.thresholds = new ThresholdConfig();
  }

  public RiskAssessmentConfig(
      boolean enabled, Map<String, SignalConfig> signals, ThresholdConfig thresholds) {
    this.enabled = enabled;
    this.signals = signals;
    this.thresholds = thresholds;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isSignalEnabled(RiskSignalType signalType) {
    SignalConfig config = signals.get(signalType.value());
    return config != null && config.enabled;
  }

  public double signalWeight(RiskSignalType signalType) {
    SignalConfig config = signals.get(signalType.value());
    return config != null ? config.weight : 0.0;
  }

  public int lookbackDays(RiskSignalType signalType) {
    SignalConfig config = signals.get(signalType.value());
    return config != null ? config.lookbackDays : 90;
  }

  public double speedThresholdKmh() {
    SignalConfig config = signals.get(RiskSignalType.IMPOSSIBLE_TRAVEL.value());
    return config != null ? config.speedThresholdKmh : 1000.0;
  }

  public RiskLevel resolveLevel(double score) {
    if (score <= thresholds.lowMax) {
      return RiskLevel.LOW;
    }
    if (score >= thresholds.highMin) {
      return RiskLevel.HIGH;
    }
    return RiskLevel.MEDIUM;
  }

  public boolean exists() {
    return signals != null && !signals.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("enabled", enabled);
    Map<String, Object> signalMap = new HashMap<>();
    if (signals != null) {
      for (Map.Entry<String, SignalConfig> entry : signals.entrySet()) {
        signalMap.put(entry.getKey(), entry.getValue().toMap());
      }
    }
    map.put("signals", signalMap);
    if (thresholds != null) {
      map.put("thresholds", thresholds.toMap());
    }
    return map;
  }

  public static class SignalConfig implements JsonReadable {
    boolean enabled;
    double weight;
    int lookbackDays;
    double speedThresholdKmh;

    public SignalConfig() {}

    public SignalConfig(
        boolean enabled, double weight, int lookbackDays, double speedThresholdKmh) {
      this.enabled = enabled;
      this.weight = weight;
      this.lookbackDays = lookbackDays;
      this.speedThresholdKmh = speedThresholdKmh;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("enabled", enabled);
      map.put("weight", weight);
      map.put("lookback_days", lookbackDays);
      map.put("speed_threshold_kmh", speedThresholdKmh);
      return map;
    }
  }

  public static class ThresholdConfig implements JsonReadable {
    double lowMax = 0.3;
    double highMin = 0.7;

    public ThresholdConfig() {}

    public ThresholdConfig(double lowMax, double highMin) {
      this.lowMax = lowMax;
      this.highMin = highMin;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("low_max", lowMax);
      map.put("high_min", highMin);
      return map;
    }
  }
}
