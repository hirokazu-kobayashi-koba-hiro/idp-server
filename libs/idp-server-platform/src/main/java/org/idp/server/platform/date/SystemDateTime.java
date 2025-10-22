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

package org.idp.server.platform.date;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Thread-safe system date/time provider with configurable timezone.
 *
 * <p>This class provides a centralized clock for the application that can be configured once at
 * startup. The clock field uses {@code volatile} to ensure visibility across threads, and the
 * configure method is {@code synchronized} to prevent race conditions.
 *
 * <h3>Thread Safety Guarantees</h3>
 *
 * <ul>
 *   <li><b>Visibility</b>: The {@code volatile} modifier on the clock field ensures that changes
 *       made by one thread are immediately visible to all other threads (Java Memory Model
 *       happens-before guarantee).
 *   <li><b>Atomicity</b>: The {@code synchronized} modifier on configure() prevents multiple
 *       threads from simultaneously modifying the clock, avoiding partial writes.
 *   <li><b>Immutability</b>: Once configured, the clock cannot be reconfigured, preventing runtime
 *       timezone changes that could cause timestamp inconsistencies.
 * </ul>
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // At application startup (once):
 * SystemDateTime.configure(ZoneId.of("Asia/Tokyo"));
 *
 * // Throughout the application:
 * LocalDateTime now = SystemDateTime.now();
 * long epochSecond = SystemDateTime.toEpochSecond(now);
 * }</pre>
 *
 * @see java.time.Clock
 * @see java.time.ZoneId
 */
public class SystemDateTime {

  /**
   * The system clock instance. Declared volatile to ensure visibility of changes across threads
   * without requiring synchronization on read operations.
   */
  private static volatile Clock clock = Clock.systemUTC();

  /**
   * Flag to prevent reconfiguration. Once configure() is called successfully, this flag is set to
   * true and any subsequent calls will throw UnsupportedOperationException.
   */
  private static boolean configured = false;

  /**
   * Configures the system clock with the specified timezone. This method can only be called once
   * during application initialization.
   *
   * <p>This method is synchronized to prevent race conditions when multiple threads attempt to
   * configure the clock simultaneously. The configured flag ensures that once successfully
   * configured, the clock cannot be changed.
   *
   * @param zone the timezone to use for the system clock
   * @throws IllegalArgumentException if zone is null
   * @throws UnsupportedOperationException if the clock has already been configured
   */
  public static synchronized void configure(ZoneId zone) {
    if (configured) {
      throw new UnsupportedOperationException(
          "Clock is already configured (zone=" + clock.getZone() + ")");
    }
    if (zone == null) {
      throw new IllegalArgumentException("Zone cannot be null");
    }
    clock = Clock.system(zone);
    configured = true;
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public static long toEpochSecond(LocalDateTime localDateTime) {
    return localDateTime.atZone(clock.getZone()).toEpochSecond();
  }

  public static long currentEpochMilliSecond() {
    return now().atZone(clock.getZone()).toInstant().toEpochMilli();
  }
}
