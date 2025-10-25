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

package org.idp.server.platform.health;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of a health check operation.
 *
 * <p>Contains overall health status and detailed status of individual components.
 */
public class HealthCheckResult {

  private final HealthStatus status;

  private HealthCheckResult(HealthStatus status) {
    this.status = status;
  }

  public HealthStatus status() {
    return status;
  }

  public boolean isHealthy() {
    return status == HealthStatus.UP;
  }

  /**
   * Converts the health check result to a Map for JSON serialization.
   *
   * @return Map representation of the health check result
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("status", status.name());
    return result;
  }

  public static class Builder {
    private HealthStatus status;

    public Builder status(HealthStatus status) {
      this.status = status;
      return this;
    }

    public HealthCheckResult build() {
      return new HealthCheckResult(status);
    }
  }
}
