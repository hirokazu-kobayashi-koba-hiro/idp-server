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

import java.util.HashMap;
import java.util.Map;

public class SecurityEventSubject {
  SecuritySubjectFormat format;
  SecurityEventSubjectPayload payload;

  public SecurityEventSubject() {}

  public SecurityEventSubject(SecuritySubjectFormat format, SecurityEventSubjectPayload payload) {
    this.format = format;
    this.payload = payload;
  }

  public SecuritySubjectFormat format() {
    return format;
  }

  public SecurityEventSubjectPayload payload() {
    return payload;
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<>();
    map.put("format", format.name());
    map.putAll(payload.toMap());
    return map;
  }
}
