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

package org.idp.server.control_plane.management.statistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.statistics.TenantStatistics;

public class TenantStatisticsResponse {

  private final TenantStatisticsStatus status;
  private final Map<String, Object> contents;

  public TenantStatisticsResponse(TenantStatisticsStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public static TenantStatisticsResponse success(
      List<TenantStatistics> monthlyStatistics, long totalCount, int limit, int offset) {
    Map<String, Object> contents = new HashMap<>();
    contents.put("list", monthlyStatistics.stream().map(TenantStatistics::toMap).toList());
    contents.put("total_count", totalCount);
    contents.put("limit", limit);
    contents.put("offset", offset);
    return new TenantStatisticsResponse(TenantStatisticsStatus.OK, contents);
  }

  public static TenantStatisticsResponse error(
      TenantStatisticsStatus status, Map<String, Object> errorContents) {
    return new TenantStatisticsResponse(status, errorContents);
  }

  public TenantStatisticsStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public String toJson() {
    return JsonConverter.snakeCaseInstance().write(contents);
  }
}
