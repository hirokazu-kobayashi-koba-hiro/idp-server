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

package org.idp.server.authentication.interactors.sms;

import java.util.Map;
import java.util.function.BiConsumer;

public class SmsAuthenticationExecutionResult {

  SmsAuthenticationExecutionStatus status;
  Map<String, Object> contents;

  private SmsAuthenticationExecutionResult(
      SmsAuthenticationExecutionStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public static SmsAuthenticationExecutionResult success(Map<String, Object> contents) {
    return new SmsAuthenticationExecutionResult(SmsAuthenticationExecutionStatus.OK, contents);
  }

  public static SmsAuthenticationExecutionResult clientError(Map<String, Object> contents) {
    return new SmsAuthenticationExecutionResult(
        SmsAuthenticationExecutionStatus.CLIENT_ERROR, contents);
  }

  public static SmsAuthenticationExecutionResult serverError(Map<String, Object> contents) {
    return new SmsAuthenticationExecutionResult(
        SmsAuthenticationExecutionStatus.SERVER_ERROR, contents);
  }

  public SmsAuthenticationExecutionStatus status() {
    return status;
  }

  public boolean isSuccess() {
    return status.isOk();
  }

  public boolean isClientError() {
    return status.isClientError();
  }

  public boolean isServerError() {
    return status.isServerError();
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public String getValueAsStringFromContents(String key) {
    return (String) contents.get(key);
  }

  public String optValueAsStringFromContents(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) contents.get(key);
    }
    return defaultValue;
  }

  public int getValueAsIntFromContents(String key) {
    return (int) contents.get(key);
  }

  public int optValueAsIntFromContents(String key, int defaultValue) {
    if (containsKey(key)) {
      return (int) contents.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return contents.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    contents.forEach(action);
  }

  public boolean exists() {
    return contents != null && !contents.isEmpty();
  }

  public int statusCode() {
    return status.code();
  }
}
