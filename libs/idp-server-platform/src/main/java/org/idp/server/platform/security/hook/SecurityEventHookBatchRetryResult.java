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

package org.idp.server.platform.security.hook;

import java.util.ArrayList;
import java.util.List;

/** Result of a batch security event hook retry operation. */
public class SecurityEventHookBatchRetryResult {

  private final List<SecurityEventHookRetryResult> results;
  private final int totalCount;
  private final int successfulCount;
  private final int failedCount;
  private final int skippedCount;

  private SecurityEventHookBatchRetryResult(List<SecurityEventHookRetryResult> results) {
    this.results = List.copyOf(results);
    this.totalCount = results.size();
    this.successfulCount =
        (int) results.stream().filter(SecurityEventHookRetryResult::isSuccess).count();
    this.failedCount =
        (int) results.stream().filter(SecurityEventHookRetryResult::isFailure).count();
    this.skippedCount =
        (int) results.stream().filter(SecurityEventHookRetryResult::isAlreadySuccessful).count();
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<SecurityEventHookRetryResult> results() {
    return results;
  }

  public int totalCount() {
    return totalCount;
  }

  public int successfulCount() {
    return successfulCount;
  }

  public int failedCount() {
    return failedCount;
  }

  public int skippedCount() {
    return skippedCount;
  }

  public boolean hasFailures() {
    return failedCount > 0;
  }

  public boolean allSuccessful() {
    return failedCount == 0 && skippedCount == 0;
  }

  public static class Builder {
    private final List<SecurityEventHookRetryResult> results = new ArrayList<>();

    public Builder addResult(SecurityEventHookRetryResult result) {
      results.add(result);
      return this;
    }

    public Builder addResults(List<SecurityEventHookRetryResult> results) {
      this.results.addAll(results);
      return this;
    }

    public SecurityEventHookBatchRetryResult build() {
      return new SecurityEventHookBatchRetryResult(results);
    }
  }
}
