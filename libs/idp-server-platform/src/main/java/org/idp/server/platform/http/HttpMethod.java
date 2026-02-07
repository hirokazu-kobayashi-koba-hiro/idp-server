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

package org.idp.server.platform.http;

import org.idp.server.platform.exception.InvalidConfigurationException;

public enum HttpMethod {
  GET,
  POST,
  PUT,
  DELETE,
  ;

  public static HttpMethod of(String method) {

    for (HttpMethod httpMethod : HttpMethod.values()) {
      if (httpMethod.name().equalsIgnoreCase(method)) {
        return httpMethod;
      }
    }

    throw new InvalidConfigurationException("Unsupported HTTP method: " + method);
  }

  public boolean isGet() {
    return this == GET;
  }

  public boolean isPost() {
    return this == POST;
  }

  public boolean isPut() {
    return this == PUT;
  }

  public boolean isDelete() {
    return this == DELETE;
  }
}
