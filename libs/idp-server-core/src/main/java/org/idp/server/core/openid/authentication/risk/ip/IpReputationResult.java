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
package org.idp.server.core.openid.authentication.risk.ip;

public class IpReputationResult {

  double score;
  String type;

  public IpReputationResult() {
    this.score = 0.0;
    this.type = "normal";
  }

  public IpReputationResult(double score, String type) {
    this.score = score;
    this.type = type;
  }

  public double score() {
    return score;
  }

  public String type() {
    return type;
  }

  public boolean exists() {
    return !"normal".equals(type);
  }

  public static IpReputationResult normal() {
    return new IpReputationResult();
  }
}
