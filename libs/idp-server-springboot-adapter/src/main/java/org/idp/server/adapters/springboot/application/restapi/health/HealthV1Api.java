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

package org.idp.server.adapters.springboot.application.restapi.health;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.platform.health.HealthCheckApi;
import org.idp.server.platform.health.HealthCheckResult;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API endpoint for tenant-specific health checks.
 *
 * <p>Provides health status information including database connectivity checks. This endpoint is
 * separate from the Spring Boot Actuator /actuator/health endpoint and provides tenant-aware health
 * information.
 *
 * <p>Endpoint: /{tenant-id}/v1/health
 *
 * <p>Response format:
 *
 * <pre>{@code
 * {
 *   "status": "UP",
 * }
 * }</pre>
 */
@RestController
@RequestMapping
public class HealthV1Api implements ParameterTransformable {

  private final HealthCheckApi healthCheckApi;

  public HealthV1Api(IdpServerApplication idpServerApplication) {
    this.healthCheckApi = idpServerApplication.healthCheckApi();
  }

  /**
   * Performs a health check for the specified tenant.
   *
   * <p>Checks database connectivity using the app database connection. The database type is
   * determined from the application configuration.
   *
   * @param tenantIdentifier the tenant identifier
   * @return ResponseEntity with health status (200 if UP, 503 if DOWN)
   */
  @GetMapping("{tenant-id}/v1/health")
  public ResponseEntity<Map<String, Object>> checkHealth(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier) {

    // Perform health check with app database connection
    HealthCheckResult result = healthCheckApi.check(tenantIdentifier);

    // Return 503 Service Unavailable if not healthy
    HttpStatus status = result.isHealthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

    return new ResponseEntity<>(result.toMap(), status);
  }
}
