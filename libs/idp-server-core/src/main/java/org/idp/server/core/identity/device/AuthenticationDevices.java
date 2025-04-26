package org.idp.server.core.identity.device;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AuthenticationDevices implements Iterable<AuthenticationDevice> {
  List<AuthenticationDevice> values;

  public AuthenticationDevices() {
    this.values = new ArrayList<>();
  }

  public AuthenticationDevices(List<AuthenticationDevice> values) {
    this.values = values;
  }

  @Override
  public Iterator<AuthenticationDevice> iterator() {
    return values.iterator();
  }
}
