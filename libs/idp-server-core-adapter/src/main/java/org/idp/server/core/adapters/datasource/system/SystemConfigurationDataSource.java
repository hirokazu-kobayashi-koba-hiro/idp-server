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

package org.idp.server.core.adapters.datasource.system;

import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.system.SystemConfigurationRepository;

public class SystemConfigurationDataSource implements SystemConfigurationRepository {

  private final SystemConfigurationSqlExecutor executor;
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public SystemConfigurationDataSource(SystemConfigurationSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public SystemConfiguration find() {
    Map<String, String> result = executor.selectOne();

    if (result == null || result.isEmpty()) {
      // No configuration in database = disabled (safe default for OSS)
      return SystemConfiguration.disabledConfiguration();
    }

    String configurationJson = result.get("configuration");
    if (configurationJson == null || configurationJson.isEmpty()) {
      return SystemConfiguration.disabledConfiguration();
    }

    return jsonConverter.read(configurationJson, SystemConfiguration.class);
  }

  @Override
  public void register(SystemConfiguration configuration) {
    executor.upsert(configuration);
  }
}
