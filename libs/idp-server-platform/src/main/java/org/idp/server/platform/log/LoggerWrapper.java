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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerWrapper {

  Logger logger;

  public static LoggerWrapper getLogger(Class<?> clazz) {
    return new LoggerWrapper(clazz);
  }

  private LoggerWrapper(Class<?> clazz) {
    this.logger = LoggerFactory.getLogger(clazz);
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
}
