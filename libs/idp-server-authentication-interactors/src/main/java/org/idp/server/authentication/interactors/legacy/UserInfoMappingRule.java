package org.idp.server.authentication.interactors.legacy;

import org.idp.server.basic.json.JsonReadable;

public class UserInfoMappingRule implements JsonReadable {
  String from;
  String to;
  String type;

  public UserInfoMappingRule() {}

  public UserInfoMappingRule(String from, String to, String type) {
    this.from = from;
    this.to = to;
    this.type = type;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public String getType() {
    return type;
  }
}
