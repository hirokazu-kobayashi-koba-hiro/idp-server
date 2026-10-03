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

package org.idp.server.authentication.interactors.attribute_verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * One item of an attribute verification: which request field holds the end-user's answer, which
 * registered attribute it is checked against, and how both are normalized first.
 */
public class AttributeVerificationField {

  String input;
  String userAttribute;
  AttributeNormalization normalization;
  int suffixLength;

  public AttributeVerificationField(
      String input, String userAttribute, AttributeNormalization normalization, int suffixLength) {
    this.input = input;
    this.userAttribute = userAttribute;
    this.normalization = normalization;
    this.suffixLength = suffixLength;
  }

  /** The request field the end-user's answer is read from. */
  public String input() {
    return input;
  }

  /** The registered attribute it is compared against (see {@link VerifiableUserAttributes}). */
  public String userAttribute() {
    return userAttribute;
  }

  /**
   * How the authorization view should ask for this field. Only settings: which input, what it is
   * compared against and in what form — never the registered value.
   */
  public Map<String, Object> toViewHint() {
    Map<String, Object> hint = new HashMap<>();
    hint.put("input", input);
    hint.put("user_attribute", userAttribute);
    hint.put("normalize", normalization.value());
    hint.put("suffix_length", suffixLength);
    return hint;
  }

  /**
   * Whether {@code submitted} matches {@code registered} once both are normalized.
   *
   * <p>A missing value on either side never matches: a user with no birthdate registered cannot be
   * verified by birthdate, whatever is submitted. Compared in constant time, so the response time
   * says nothing about how much of a guess was right.
   */
  public boolean matches(String submitted, String registered) {
    String left = prepare(submitted);
    String right = prepare(registered);
    if (left == null || right == null) {
      return false;
    }
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Normalizes, then keeps the last {@code suffixLength} characters when one is set. A value
   * shorter than that cannot be compared and yields null.
   */
  private String prepare(String value) {
    String normalized = normalization.apply(value);
    if (normalized == null || suffixLength <= 0) {
      return normalized;
    }
    if (normalized.length() < suffixLength) {
      return null;
    }
    return normalized.substring(normalized.length() - suffixLength);
  }
}
