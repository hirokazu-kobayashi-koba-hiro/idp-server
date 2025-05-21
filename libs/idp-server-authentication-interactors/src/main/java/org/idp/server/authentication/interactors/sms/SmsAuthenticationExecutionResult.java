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
