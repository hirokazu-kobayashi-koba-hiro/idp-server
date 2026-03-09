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

public class RiskSignalResult {

  RiskSignalType signalType;
  double score;
  String reason;

  public RiskSignalResult() {}

  public RiskSignalResult(RiskSignalType signalType, double score, String reason) {
    this.signalType = signalType;
    this.score = score;
    this.reason = reason;
  }

  public RiskSignalType signalType() {
    return signalType;
  }

  public double score() {
    return score;
  }

  public String reason() {
    return reason;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("signal", signalType.value());
    map.put("score", score);
    map.put("reason", reason);
    return map;
  }
}
