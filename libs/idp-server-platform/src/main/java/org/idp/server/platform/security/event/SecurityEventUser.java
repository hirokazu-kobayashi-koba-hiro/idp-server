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

package org.idp.server.platform.security.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.server.platform.uuid.UuidConvertable;

public class SecurityEventUser implements UuidConvertable {
  String id;
  String name;
  String exSub;
  String email;
  String phoneNumber;

  public SecurityEventUser() {}

  public SecurityEventUser(String id, String name, String exSub, String email, String phoneNumber) {
    this.id = id;
    this.name = name;
    this.exSub = exSub;
    this.email = email;
    this.phoneNumber = phoneNumber;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    if (id != null) {
      result.put("id", id);
    }
    if (name != null) {
      result.put("name", name);
    }
    if (exSub != null) {
      result.put("sub", exSub);
    }
    if (email != null) {
      result.put("email", email);
    }
    if (phoneNumber != null) {
      result.put("phone_number", phoneNumber);
    }
    return result;
  }

  public String id() {
    return id;
  }

  public UUID idAsUuid() {
    return convertUuid(id);
  }

  public String name() {
    return name;
  }

  public String exSub() {
    return exSub;
  }

  public String email() {
    return email;
  }

  public String phoneNumber() {
    return phoneNumber;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.isEmpty();
  }
}
