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
package org.idp.server.platform.multi_tenancy.tenant.policy;

import java.util.HashMap;
import java.util.Map;

public class RiskAssessmentConfig {

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

  @SuppressWarnings("unchecked")
  public static RiskAssessmentConfig fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return new RiskAssessmentConfig();
    }
    boolean enabled = Boolean.TRUE.equals(map.get("enabled"));
    Map<String, SignalConfig> signals = new HashMap<>();
    if (map.containsKey("signals") && map.get("signals") instanceof Map) {
      Map<String, Object> signalsMap = (Map<String, Object>) map.get("signals");
      for (Map.Entry<String, Object> entry : signalsMap.entrySet()) {
        if (entry.getValue() instanceof Map) {
          Map<String, Object> sc = (Map<String, Object>) entry.getValue();
          signals.put(entry.getKey(), SignalConfig.fromMap(sc));
        }
      }
    }
    ThresholdConfig thresholds = ThresholdConfig.defaultThresholds();
    if (map.containsKey("thresholds") && map.get("thresholds") instanceof Map) {
      Map<String, Object> tc = (Map<String, Object>) map.get("thresholds");
      thresholds = ThresholdConfig.fromMap(tc);
    }
    return new RiskAssessmentConfig(enabled, signals, thresholds);
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

  public static class SignalConfig {
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

    public static SignalConfig fromMap(Map<String, Object> map) {
      boolean enabled = Boolean.TRUE.equals(map.get("enabled"));
      double weight = toDouble(map.get("weight"), 0.0);
      int lookbackDays = toInt(map.get("lookback_days"), 90);
      double speedThresholdKmh = toDouble(map.get("speed_threshold_kmh"), 1000.0);
      return new SignalConfig(enabled, weight, lookbackDays, speedThresholdKmh);
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

  public static class ThresholdConfig {
    double lowMax = 0.3;
    double highMin = 0.7;

    public ThresholdConfig() {}

    public ThresholdConfig(double lowMax, double highMin) {
      this.lowMax = lowMax;
      this.highMin = highMin;
    }

    public static ThresholdConfig defaultThresholds() {
      return new ThresholdConfig(0.3, 0.7);
    }

    public static ThresholdConfig fromMap(Map<String, Object> map) {
      double lowMax = toDouble(map.get("low_max"), 0.3);
      double highMin = toDouble(map.get("high_min"), 0.7);
      return new ThresholdConfig(lowMax, highMin);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("low_max", lowMax);
      map.put("high_min", highMin);
      return map;
    }
  }

  private static double toDouble(Object value, double defaultValue) {
    if (value == null) return defaultValue;
    if (value instanceof Number) return ((Number) value).doubleValue();
    return defaultValue;
  }

  private static int toInt(Object value, int defaultValue) {
    if (value == null) return defaultValue;
    if (value instanceof Number) return ((Number) value).intValue();
    return defaultValue;
  }
}
