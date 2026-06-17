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

package org.idp.server.core.extension.identity.verification.application.pre_hook;

import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.ConditionSpec;

/**
 * One assertion evaluated by the {@code assert} pre_hook verifier: a {@link ConditionSpec} that
 * MUST hold against the verifier context (which includes {@code $.previous_applications}, {@code
 * $.user}, {@code $.application}, {@code $.request_body}, {@code $.request_attributes}). When the
 * condition is not satisfied the application is denied with {@link #message()}.
 *
 * <p>Reusing {@link ConditionSpec} means the assertion shares the exact condition language
 * (operations + {@code allOf}/{@code anyOf}) used by the gate {@code condition} on a verification,
 * so a CDD-style rule such as "an approved past application of this type must exist" is expressible
 * declaratively over {@code $.previous_applications}.
 */
public class AssertionRule implements JsonReadable {

  ConditionSpec condition;
  String message;

  public AssertionRule() {}

  public AssertionRule(ConditionSpec condition, String message) {
    this.condition = condition;
    this.message = message;
  }

  public ConditionSpec condition() {
    return condition;
  }

  public boolean hasCondition() {
    return condition != null;
  }

  public String message() {
    return message != null && !message.isEmpty() ? message : "assertion failed";
  }
}
