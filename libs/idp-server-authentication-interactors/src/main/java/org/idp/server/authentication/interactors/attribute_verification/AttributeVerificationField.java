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
import org.idp.server.platform.mapper.ValueFunctionChain;

/**
 * One item of an attribute verification: which request field holds the end-user's answer, which
 * registered attribute it is checked against, and how both are brought to the same form first — a
 * named {@link AttributeNormalization} or the tenant's own {@code functions}.
 */
public class AttributeVerificationField {

  String input;
  String userAttribute;
  ValueFunctionChain chain;
  String normalization;
  int suffixLength;

  public AttributeVerificationField(
      String input, String userAttribute, AttributeNormalization normalization, int suffixLength) {
    this(input, userAttribute, normalization.chain(), normalization.value(), suffixLength);
  }

  /**
   * @param chain the functions applied to both sides, built for comparison
   * @param normalization the named normalization the chain came from, or null for custom functions
   */
  public AttributeVerificationField(
      String input,
      String userAttribute,
      ValueFunctionChain chain,
      String normalization,
      int suffixLength) {
    this.input = input;
    this.userAttribute = userAttribute;
    this.chain = chain;
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
   * Applies the chain, then keeps the last {@code suffixLength} characters when one is set.
   *
   * <p>An empty result is no value, not a value: a chain can reduce anything to the empty string —
   * {@code regex_replace} removing every character, {@code date} on something that is not a date —
   * and two empty strings would otherwise match. A value shorter than {@code suffixLength} cannot
   * be compared either. Both yield null, which matches nothing.
   */
  private String prepare(String value) {
    if (value == null) {
      return null;
    }
    Object transformed = chain.apply(value);
    String normalized = transformed == null ? null : transformed.toString();
    if (normalized == null || normalized.isEmpty()) {
      return null;
    }
    if (suffixLength <= 0) {
      return normalized;
    }
    if (normalized.length() < suffixLength) {
      return null;
    }
    return normalized.substring(normalized.length() - suffixLength);
  }
}
