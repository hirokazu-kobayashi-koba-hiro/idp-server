package org.idp.server.core.type.extension;

/** ResponseModeValue ? or # */
public class ResponseModeValue {
  String value;

  public ResponseModeValue() {}

  public ResponseModeValue(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
