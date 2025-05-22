/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
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
