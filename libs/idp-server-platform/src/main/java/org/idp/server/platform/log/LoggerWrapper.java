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

package org.idp.server.platform.log;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerWrapper {

  Logger logger;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public static LoggerWrapper getLogger(Class<?> clazz) {
    return new LoggerWrapper(clazz);
  }

  private LoggerWrapper(Class<?> clazz) {
    this.logger = LoggerFactory.getLogger(clazz);
  }

  public void trace(String message, Object... args) {
    logger.trace(message, args);
  }

  public void trace(String message) {
    logger.trace(message);
  }

  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public void debug(String message, Object... args) {
    logger.debug(message, args);
  }

  public void debug(String message) {
    logger.debug(message);
  }

  public void info(String message) {
    logger.info(message);
  }

  public void info(String message, Object... args) {
    logger.info(message, args);
  }

  public void warn(String message, Object... args) {
    logger.warn(message, args);
  }

  public void warn(String message) {
    logger.warn(message);
  }

  public void error(String message, Object... args) {
    logger.error(message, args);
  }

  public void error(String message) {
    logger.error(message);
  }

  public void error(String message, Throwable throwable) {
    logger.error(message, throwable);
  }

  /**
   * Logs a message with structured data as JSON at TRACE level. The data is serialized as JSON
   * string and appended to the message. Logback JsonEncoder will parse this JSON and make fields
   * searchable in Datadog (e.g., @status:INVALID_TOKEN).
   *
   * @param message The message to log
   * @param data Structured data to be serialized as JSON
   */
  public void traceJson(String message, Map<String, Object> data) {
    if (logger.isTraceEnabled()) {
      logger.trace("{} {}", message, jsonConverter.write(data));
    }
  }

  /**
   * Logs a message with structured data as JSON at DEBUG level. The data is serialized as JSON
   * string and appended to the message. Logback JsonEncoder will parse this JSON and make fields
   * searchable in Datadog (e.g., @status:INVALID_TOKEN).
   *
   * @param message The message to log
   * @param data Structured data to be serialized as JSON
   */
  public void debugJson(String message, Map<String, Object> data) {
    if (logger.isDebugEnabled()) {
      logger.debug("{} {}", message, jsonConverter.write(data));
    }
  }

  /**
   * Logs a message with structured data as JSON at INFO level. The data is serialized as JSON
   * string and appended to the message. Logback JsonEncoder will parse this JSON and make fields
   * searchable in Datadog (e.g., @status:INVALID_TOKEN).
   *
   * @param message The message to log
   * @param data Structured data to be serialized as JSON
   */
  public void infoJson(String message, Map<String, Object> data) {
    logger.info("{} {}", message, jsonConverter.write(data));
  }

  /**
   * Logs a message with structured data as JSON at WARN level. The data is serialized as JSON
   * string and appended to the message. Logback JsonEncoder will parse this JSON and make fields
   * searchable in Datadog (e.g., @status:INVALID_TOKEN).
   *
   * @param message The message to log
   * @param data Structured data to be serialized as JSON
   */
  public void warnJson(String message, Map<String, Object> data) {
    logger.warn("{} {}", message, jsonConverter.write(data));
  }

  /**
   * Logs a message with structured data as JSON at ERROR level. The data is serialized as JSON
   * string and appended to the message. Logback JsonEncoder will parse this JSON and make fields
   * searchable in Datadog (e.g., @status:INVALID_TOKEN).
   *
   * @param message The message to log
   * @param data Structured data to be serialized as JSON
   */
  public void errorJson(String message, Map<String, Object> data) {
    logger.error("{} {}", message, jsonConverter.write(data));
  }

  /**
   * Logs a message with structured data and exception as JSON at ERROR level. The data is
   * serialized as JSON string and appended to the message. Logback JsonEncoder will parse this JSON
   * and make fields searchable in Datadog (e.g., @status:INVALID_TOKEN).
   *
   * @param message The message to log
   * @param data Structured data to be serialized as JSON
   * @param throwable The exception to log
   */
  public void errorJson(String message, Map<String, Object> data, Throwable throwable) {
    Map<String, Object> enrichedData = new HashMap<>(data);
    enrichedData.put("exception", throwable.getClass().getName());
    enrichedData.put("exception_message", throwable.getMessage());
    logger.error("{} {}", message, jsonConverter.write(enrichedData), throwable);
  }
}
