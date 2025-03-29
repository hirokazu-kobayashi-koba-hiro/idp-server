package org.idp.server.core.type.extension;

/** ResponseModeValue ? or # */
public class ResponseModeValue {
  String value;

  public ResponseModeValue() {}

  public ResponseModeValue(String value) {
    this.value = value;
  }

  public static ResponseModeValue query() {
    return new ResponseModeValue("?");
  }

  public static ResponseModeValue fragment() {
    return new ResponseModeValue("#");
  }

  public String value() {
    return value;
  }
}
