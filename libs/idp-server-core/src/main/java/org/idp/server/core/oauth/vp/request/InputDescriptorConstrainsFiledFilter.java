package org.idp.server.core.oauth.vp.request;

import org.idp.server.basic.json.JsonReadable;

public class InputDescriptorConstrainsFiledFilter implements JsonReadable {
  String type;
  String pattern;

  public InputDescriptorConstrainsFiledFilter() {}

  public InputDescriptorConstrainsFiledFilter(String type, String pattern) {
    this.type = type;
    this.pattern = pattern;
  }

  public String type() {
    return type;
  }

  public String pattern() {
    return pattern;
  }
}
