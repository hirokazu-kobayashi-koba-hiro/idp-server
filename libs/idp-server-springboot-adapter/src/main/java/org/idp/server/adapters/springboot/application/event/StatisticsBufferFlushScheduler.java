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

package org.idp.server.adapters.springboot.application.event;

import jakarta.annotation.PreDestroy;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.statistics.StatisticsBufferFlushApi;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for periodic statistics buffer flushing.
 *
 * <p>This component provides:
 *
 * <ul>
 *   <li>Periodic flush every 5 seconds to write buffered statistics to database
 *   <li>Shutdown hook to flush remaining buffer on application shutdown
 * </ul>
 *
 * @see StatisticsBufferFlushApi
 */
@Component
public class StatisticsBufferFlushScheduler {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(StatisticsBufferFlushScheduler.class);

  private final StatisticsBufferFlushApi flushApi;

  public StatisticsBufferFlushScheduler(IdpServerApplication idpServerApplication) {
    this.flushApi = idpServerApplication.statisticsBufferFlushApi();
  }

  /**
   * Flush all tenant buffers periodically.
   *
   * <p>Runs every 5 seconds to write accumulated statistics to the database.
   */
  @Scheduled(fixedRate = 5000)
  public void flushStatistics() {
    int flushed = flushApi.flushAll();
    if (flushed > 0) {
      log.debug("Flushed {} statistics records from scheduled task", flushed);
    }
  }

  /**
   * Flush remaining buffer on application shutdown.
   *
   * <p>Ensures no statistics data is lost when the application stops.
   */
  @PreDestroy
  public void onShutdown() {
    log.info("Application shutting down - flushing statistics buffer");
    int flushed = flushApi.flushAll();
    if (flushed > 0) {
      log.info("Flushed {} statistics records on shutdown", flushed);
    } else {
      log.info("No statistics records to flush on shutdown");
    }
  }
}
