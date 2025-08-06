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

package org.idp.server.security.event.hook.ssf.io;

import java.util.Map;

public class SharedSignalsFrameworkConfigurationRequestResponse {

  SharedSignalsFrameworkConfigurationRequestStatus status;
  Map<String, Object> content;

  public SharedSignalsFrameworkConfigurationRequestResponse(
      SharedSignalsFrameworkConfigurationRequestStatus status, Map<String, Object> content) {
    this.status = status;
    this.content = content;
  }

  public SharedSignalsFrameworkConfigurationRequestStatus status() {
    return status;
  }

  public Map<String, Object> content() {
    return content;
  }

  public int statusCode() {
    return status.statusCode();
  }
}
