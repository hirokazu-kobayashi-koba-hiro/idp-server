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

/**
 * Represents the overall health status of the system or a component.
 *
 * <p>Follows Spring Boot Actuator health status conventions.
 */
public enum HealthStatus {
  /** The component or system is functioning correctly */
  UP,

  /** The component or system is not functioning correctly */
  DOWN,

  /** The component or system may be functioning at a degraded level */
  DEGRADED,

  /** The health status is unknown */
  UNKNOWN
}
