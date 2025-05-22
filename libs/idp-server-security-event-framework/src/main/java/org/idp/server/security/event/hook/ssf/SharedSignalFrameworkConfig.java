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


package org.idp.server.security.event.hook.ssf;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class SharedSignalFrameworkConfig implements JsonReadable {

  String privateKey;
  String endpoint;
  Map<String, String> headers;

  public SharedSignalFrameworkConfig() {}

  public SharedSignalFrameworkConfig(
      String privateKey, String endpoint, Map<String, String> headers) {
    this.privateKey = privateKey;
    this.endpoint = endpoint;
    this.headers = headers;
  }

  public String privateKey() {
    return privateKey;
  }

  public String endpoint() {
    return endpoint;
  }

  public Map<String, String> headers() {
    return headers;
  }
}
