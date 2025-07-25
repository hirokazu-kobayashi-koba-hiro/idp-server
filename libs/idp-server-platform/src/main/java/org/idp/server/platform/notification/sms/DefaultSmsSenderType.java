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

package org.idp.server.platform.notification.sms;

import org.idp.server.platform.exception.UnSupportedException;

public enum DefaultSmsSenderType {
  EXTERNAL_API_SERVICE("external_api_service"),
  NO_ACTION("no_action");

  String typeName;

  DefaultSmsSenderType(String typeName) {
    this.typeName = typeName;
  }

  public static DefaultSmsSenderType of(String type) {
    for (DefaultSmsSenderType senderType : values()) {
      if (senderType.typeName.equals(type)) {
        return senderType;
      }
    }
    throw new UnSupportedException("No SmsSenderType found for type " + type);
  }

  public SmsSenderType toType() {
    return new SmsSenderType(this.typeName);
  }
}
