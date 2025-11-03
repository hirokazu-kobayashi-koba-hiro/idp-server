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

package org.idp.server.adapters.springboot;

import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Graceful Shutdown Lifecycle Management for Kubernetes
 *
 * <p>This component implements SmartLifecycle to introduce a delay during application shutdown,
 * allowing Kubernetes to properly remove the Pod from Service endpoints before stopping the
 * application.
 *
 * <h2>Why is this needed?</h2>
 *
 * <p>When Kubernetes deletes a Pod:
 *
 * <ol>
 *   <li>Kubernetes sends SIGTERM to the container
 *   <li>Kubernetes asynchronously removes the Pod from Service endpoints (via readiness probe)
 *   <li>If the application shuts down immediately, new requests might still arrive during endpoint
 *       removal
 * </ol>
 *
 * <p>This component introduces a configurable delay to ensure:
 *
 * <ol>
 *   <li>Readiness probe becomes DOWN immediately on SIGTERM
 *   <li>Kubernetes removes Pod from Service endpoints
 *   <li>Application waits for endpoint removal to complete
 *   <li>Graceful shutdown begins after the delay
 * </ol>
 *
 * <h2>Configuration</h2>
 *
 * <pre>
 * idp:
 *   server:
 *     shutdown:
 *       delay: 5s  # Wait 5 seconds before starting graceful shutdown
 * </pre>
 *
 * <h2>Kubernetes Integration</h2>
 *
 * <pre>
 * readinessProbe:
 *   httpGet:
 *     path: /actuator/health/readiness
 *     port: 8080
 *   periodSeconds: 5
 *
 * lifecycle:
 *   preStop:
 *     exec:
 *       command: ["sh", "-c", "sleep 5"]  # Must match shutdown delay
 * </pre>
 *
 * @see org.springframework.context.SmartLifecycle
 * @see org.springframework.boot.availability.ApplicationAvailability
 */
@Component
public class GracefulShutdownLifecycle implements SmartLifecycle {

  private static final LoggerWrapper logger =
      LoggerWrapper.getLogger(GracefulShutdownLifecycle.class);

  private volatile boolean running = false;

  @Value("${idp.server.shutdown.delay:5s}")
  private String shutdownDelay;

  @Override
  public void start() {
    running = true;
    logger.info("GracefulShutdownLifecycle started");
  }

  @Override
  public void stop() {
    logger.info("GracefulShutdownLifecycle stop requested - beginning shutdown delay");
    running = false;

    try {
      long delayMillis = parseDuration(shutdownDelay);
      logger.info(
          "Waiting {}ms before graceful shutdown to allow Kubernetes endpoint removal",
          delayMillis);
      logger.info(
          "During this time, readiness probe should become DOWN and Pod should be removed from"
              + " Service");

      Thread.sleep(delayMillis);

      logger.info(
          "Shutdown delay completed - proceeding with graceful shutdown of active requests");
    } catch (InterruptedException e) {
      logger.warn("Shutdown delay interrupted - proceeding with graceful shutdown immediately");
      Thread.currentThread().interrupt();
    } finally {
      logger.info("GracefulShutdownLifecycle stopped");
    }
  }

  @Override
  public void stop(Runnable callback) {
    logger.info("GracefulShutdownLifecycle stop requested - beginning shutdown delay");
    running = false;

    try {
      long delayMillis = parseDuration(shutdownDelay);
      logger.info(
          "Waiting {}ms before graceful shutdown to allow Kubernetes endpoint removal",
          delayMillis);
      logger.info(
          "During this time, readiness probe should become DOWN and Pod should be removed from"
              + " Service");

      Thread.sleep(delayMillis);

      logger.info(
          "Shutdown delay completed - proceeding with graceful shutdown of active requests");
    } catch (InterruptedException e) {
      logger.warn("Shutdown delay interrupted - proceeding with graceful shutdown immediately");
      Thread.currentThread().interrupt();
    } finally {
      callback.run();
      logger.info("GracefulShutdownLifecycle stopped");
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    // Return maximum value to ensure this component stops last
    // This allows Spring Boot's graceful shutdown to handle active requests
    // after the Kubernetes endpoint removal delay
    return Integer.MAX_VALUE;
  }

  /**
   * Parse Spring Boot duration string to milliseconds
   *
   * @param duration Duration string (e.g., "5s", "10000ms", "1m")
   * @return Duration in milliseconds
   */
  private long parseDuration(String duration) {
    if (duration == null || duration.isEmpty()) {
      return 5000; // Default 5 seconds
    }

    duration = duration.trim().toLowerCase();

    // Parse number
    StringBuilder numberStr = new StringBuilder();
    StringBuilder unitStr = new StringBuilder();

    for (char c : duration.toCharArray()) {
      if (Character.isDigit(c) || c == '.') {
        numberStr.append(c);
      } else {
        unitStr.append(c);
      }
    }

    if (numberStr.length() == 0) {
      return 5000;
    }

    double number = Double.parseDouble(numberStr.toString());
    String unit = unitStr.toString().trim();

    // Convert to milliseconds based on unit
    return switch (unit) {
      case "ms" -> (long) number;
      case "s", "" -> (long) (number * 1000);
      case "m" -> (long) (number * 60 * 1000);
      case "h" -> (long) (number * 60 * 60 * 1000);
      default -> {
        logger.warn("Unknown duration unit: {}, using default 5s", unit);
        yield 5000L;
      }
    };
  }
}
