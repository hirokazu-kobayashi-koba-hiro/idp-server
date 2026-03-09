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
package org.idp.server.control_plane.management.risk;

import java.util.Map;

public class RiskAssessmentConfigManagementResponse {

  int statusCode;
  Map<String, Object> contents;

  public RiskAssessmentConfigManagementResponse(int statusCode, Map<String, Object> contents) {
    this.statusCode = statusCode;
    this.contents = contents;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public static RiskAssessmentConfigManagementResponse ok(Map<String, Object> contents) {
    return new RiskAssessmentConfigManagementResponse(200, contents);
  }

  public static RiskAssessmentConfigManagementResponse noContent() {
    return new RiskAssessmentConfigManagementResponse(204, Map.of());
  }

  public static RiskAssessmentConfigManagementResponse notFound() {
    return new RiskAssessmentConfigManagementResponse(404, Map.of("error", "not_found"));
  }
}
