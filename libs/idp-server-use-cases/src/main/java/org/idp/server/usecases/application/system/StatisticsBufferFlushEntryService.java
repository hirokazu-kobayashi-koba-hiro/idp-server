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

package org.idp.server.usecases.application.system;

import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.statistics.StatisticsBufferFlushApi;
import org.idp.server.platform.statistics.buffer.StatisticsBufferFlusher;

/**
 * Entry service for statistics buffer flush operations.
 *
 * <p>Provides transactional access to statistics buffer flush operations via proxy. Supports both
 * memory-based and cache-backed flush implementations.
 */
@Transaction
public class StatisticsBufferFlushEntryService implements StatisticsBufferFlushApi {

  private final StatisticsBufferFlusher flusher;

  public StatisticsBufferFlushEntryService(StatisticsBufferFlusher flusher) {
    this.flusher = flusher;
  }

  @Override
  public int flushAll() {
    return flusher.flushAll();
  }
}
