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

package org.idp.server.platform.hash;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MessageDigestableThreadSafetyTest implements MessageDigestable {

  @Test
  void digestWithSha256_shouldBeThreadSafe() throws Exception {
    int threadCount = 10;
    int iterationsPerThread = 1000;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger errorCount = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                for (int j = 0; j < iterationsPerThread; j++) {
                  try {
                    digestWithSha256("test-input-" + j);
                  } catch (Exception e) {
                    errorCount.incrementAndGet();
                  }
                }
              }));
    }

    startLatch.countDown();

    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }

    executor.shutdown();

    assertEquals(0, errorCount.get(), "concurrent digest calls should not cause errors");
  }
}
