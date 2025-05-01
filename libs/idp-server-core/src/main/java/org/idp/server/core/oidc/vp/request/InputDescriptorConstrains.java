package org.idp.server.core.oidc.vp.request;

import java.util.Iterator;
import java.util.List;
import org.idp.server.basic.json.JsonReadable;

public class InputDescriptorConstrains
    implements Iterable<InputDescriptorConstrainsFields>, JsonReadable {
  List<InputDescriptorConstrainsFields> fields;

  public InputDescriptorConstrains() {
    this.fields = List.of();
  }

  public InputDescriptorConstrains(List<InputDescriptorConstrainsFields> fileds) {
    this.fields = fileds;
  }

  @Override
  public Iterator<InputDescriptorConstrainsFields> iterator() {
    return fields.iterator();
  }

  public boolean exists() {
    return !fields.isEmpty();
  }
}
