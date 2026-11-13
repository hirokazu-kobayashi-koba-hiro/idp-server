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
package org.idp.server.platform.crypto.datakey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

/** The purposes data keys exist for, and so the ones an administrator may rotate. */
public class DataKeyPurposes {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(DataKeyPurposes.class);

  Map<String, DataKeyPurpose> purposes;

  public DataKeyPurposes(List<DataKeyPurpose> purposes) {
    this.purposes = new LinkedHashMap<>();
    purposes.forEach(purpose -> this.purposes.put(purpose.name(), purpose));
  }

  public static DataKeyPurposes load(ApplicationComponentContainer applicationComponentContainer) {
    List<DataKeyPurpose> purposes =
        PluginLoader.loadFromInternalModule(DataKeyPurposeFactory.class).stream()
            .map(factory -> factory.create(applicationComponentContainer))
            .toList();
    purposes.forEach(purpose -> log.info("Data key purpose registered: {}", purpose.name()));
    return new DataKeyPurposes(purposes);
  }

  public boolean isKnown(String purpose) {
    return purposes.containsKey(purpose);
  }

  public Set<String> names() {
    return purposes.keySet();
  }

  public Optional<DataKeyPurpose> of(String purpose) {
    return Optional.ofNullable(purposes.get(purpose));
  }

  /**
   * The purpose a rotation asks for when it names none: the only one, or none if there are more.
   */
  public Optional<String> soleName() {
    return purposes.size() == 1
        ? Optional.of(purposes.keySet().iterator().next())
        : Optional.empty();
  }
}
