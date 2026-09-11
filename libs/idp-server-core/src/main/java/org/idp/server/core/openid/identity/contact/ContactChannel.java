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

package org.idp.server.core.openid.identity.contact;

import org.idp.server.core.openid.identity.User;

/**
 * A contact channel a one-time code can be delivered over (Issue #1416).
 *
 * <p>Holds the mapping from a channel to the {@link User} attributes it owns, so neither the domain
 * service nor {@link ContactVerificationOperation} repeats a switch over channels.
 */
public enum ContactChannel {
  EMAIL("email", "/schema/1.0/contact/email-target.json") {
    @Override
    String currentValue(User user) {
      return user.email();
    }

    @Override
    void applyValue(User user, String value) {
      user.setEmail(value);
    }

    @Override
    void markVerified(User user) {
      user.setEmailVerified(true);
    }
  },
  PHONE("phone", "/schema/1.0/contact/phone-target.json") {
    @Override
    String currentValue(User user) {
      return user.phoneNumber();
    }

    @Override
    void applyValue(User user, String value) {
      user.setPhoneNumber(value);
    }

    @Override
    void markVerified(User user) {
      user.setPhoneNumberVerified(true);
    }
  };

  String value;
  String targetSchemaPath;

  ContactChannel(String value, String targetSchemaPath) {
    this.value = value;
    this.targetSchemaPath = targetSchemaPath;
  }

  abstract String currentValue(User user);

  abstract void applyValue(User user, String value);

  abstract void markVerified(User user);

  public String value() {
    return value;
  }

  String targetSchemaPath() {
    return targetSchemaPath;
  }

  public boolean isEmail() {
    return this == EMAIL;
  }

  public boolean isPhone() {
    return this == PHONE;
  }
}
