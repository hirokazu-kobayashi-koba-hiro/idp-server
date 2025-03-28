package org.idp.server.core.oauth.vp.request;

import java.util.List;
import org.idp.server.core.basic.json.JsonReadable;

public class InputDescriptorConstrainsFields implements JsonReadable {
  List<String> path;
  InputDescriptorConstrainsFiledFilter filter;

  public InputDescriptorConstrainsFields() {}

  public InputDescriptorConstrainsFields(
      List<String> path, InputDescriptorConstrainsFiledFilter filter) {
    this.path = path;
    this.filter = filter;
  }

  public List<String> path() {
    return path;
  }

  public InputDescriptorConstrainsFiledFilter filter() {
    return filter;
  }
}
