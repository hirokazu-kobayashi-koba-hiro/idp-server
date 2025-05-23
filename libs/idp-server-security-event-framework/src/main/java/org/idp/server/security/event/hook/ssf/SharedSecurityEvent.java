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

public class SharedSecurityEvent {
  SecurityEventType type;
  SecurityEventSubject subject;
  SecurityEventPayload payload;

  public SharedSecurityEvent() {}

  public SharedSecurityEvent(SecurityEventType type, SecurityEventSubject subject) {
    this.type = type;
    this.subject = subject;
  }

  public SharedSecurityEvent(
      SecurityEventType type, SecurityEventSubject subject, SecurityEventPayload payload) {
    this.type = type;
    this.subject = subject;
    this.payload = payload;
  }

  public boolean isDefined() {
    return type.isDefined();
  }

  public SecurityEventType type() {
    return type;
  }

  public SecurityEventSubject subject() {
    return subject;
  }

  public SecurityEventPayload payload() {
    return payload;
  }

  public Map<String, Object> eventAsMap() {
    HashMap<String, Object> events = new HashMap<>();
    events.put("subject", subject.toMap());
    events.putAll(payload.toMap());

    HashMap<String, Object> result = new HashMap<>();
    result.put(type.typeIdentifier().value(), events);

    return result;
  }
}
