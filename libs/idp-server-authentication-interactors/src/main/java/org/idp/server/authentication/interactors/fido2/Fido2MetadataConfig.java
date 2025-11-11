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

package org.idp.server.authentication.interactors.fido2;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class Fido2MetadataConfig implements JsonReadable {

  Map<String, Object> metadata;

  public Fido2MetadataConfig(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public String usernameParam() {
    return optValueAsString("username_param", "username");
  }

  public String optValueAsString(String key, String defaultValue) {
    if (metadata.containsKey(key)) {
      return metadata.get(key).toString();
    }
    return defaultValue;
  }
}
