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
import org.idp.server.platform.multi_tenancy.tenant.policy.UniqueKeyAttribute;

/**
 * A contact channel a one-time code can be delivered over (Issue #1416).
 *
 * <p>Holds the mapping from a channel to the {@link User} attributes it owns, so neither the domain
 * service nor {@link ContactVerificationOperation} repeats a switch over channels.
 */
public enum ContactChannel {
  EMAIL("email", "email", "email", "/schema/1.0/contact/email-target.json") {
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
  PHONE("phone", "sms", "phone_number", "/schema/1.0/contact/phone-target.json") {
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
  String authenticationConfigType;
  String targetFieldName;
  String targetSchemaPath;

  ContactChannel(
      String value,
      String authenticationConfigType,
      String targetFieldName,
      String targetSchemaPath) {
    this.value = value;
    this.authenticationConfigType = authenticationConfigType;
    this.targetFieldName = targetFieldName;
    this.targetSchemaPath = targetSchemaPath;
  }

  abstract String currentValue(User user);

  abstract void applyValue(User user, String value);

  abstract void markVerified(User user);

  /**
   * The tenant-unique-key attribute this channel owns.
   *
   * <p>Lets the policy answer "does changing this channel move the login identifier?" without the
   * caller restating the mapping.
   */
  public UniqueKeyAttribute uniqueKeyAttribute() {
    return this == EMAIL ? UniqueKeyAttribute.EMAIL : UniqueKeyAttribute.PHONE_NUMBER;
  }

  public String value() {
    return value;
  }

  /**
   * The {@code type} of the authentication configuration this channel's sending is described in.
   *
   * <p>Not the same string as {@link #value()}: the phone channel is served by the {@code sms}
   * configuration. Reusing it is the point — sender, templates, retry cap and expiry are already
   * modelled and operated there.
   */
  public String authenticationConfigType() {
    return authenticationConfigType;
  }

  /** The interaction describing how a code is issued and delivered. */
  public String challengeInteractionKey() {
    return authenticationConfigType + "-authentication-challenge";
  }

  /** The interaction describing how a submitted code is decided. */
  public String verifyInteractionKey() {
    return authenticationConfigType + "-authentication";
  }

  /**
   * The executor used when no verify interaction is configured.
   *
   * <p>Same function name the interaction would have named, so the fallback and the configured path
   * run the identical implementation. Deciding a locally generated code needs no configuration —
   * the code is on the challenge row — and a tenant whose configuration only describes sending
   * never had a reason to write one.
   */
  public String localVerifyFunction() {
    return authenticationConfigType + "_authentication";
  }

  /**
   * The request field the destination is carried in.
   *
   * <p>Taken from the {@code request.schema} the channel's interaction already declares — {@code
   * email} for email, {@code phone_number} for SMS — not from {@link #value()}. The two differ for
   * phone, and naming the field after the channel is what made a delegated phone challenge post
   * {@code {"phone": ...}} to a service that documents, validates and mocks {@code phone_number}.
   */
  public String targetFieldName() {
    return targetFieldName;
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
