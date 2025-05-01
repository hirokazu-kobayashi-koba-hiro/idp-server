package org.idp.server.core.oidc.vp.request;

import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;

public class InputDescriptor implements JsonReadable {

  String id;
  String name;
  String purpose;
  InputDescriptorConstrains constrains;

  public InputDescriptor() {}

  public String id() {
    return id;
  }

  public boolean hasId() {
    return Objects.nonNull(id) && !id.isEmpty();
  }

  public String name() {
    return name;
  }

  public boolean hasName() {
    return Objects.nonNull(name) && !name.isEmpty();
  }

  public String purpose() {
    return purpose;
  }

  public boolean hasPurpose() {
    return Objects.nonNull(purpose) && !purpose.isEmpty();
  }

  public InputDescriptorConstrains constrains() {
    return constrains;
  }

  public boolean hasConstrains() {
    return Objects.nonNull(constrains) && constrains.exists();
  }
}
