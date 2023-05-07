package org.idp.server.ciba.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.ciba.CibaRequestPattern;

public class CibaContextServices {

  Map<CibaRequestPattern, CibaRequestContextService> values;

  public CibaContextServices() {
    this.values = new HashMap<>();
    this.values.put(CibaRequestPattern.NORMAL, new NormalPatternContextService());
    this.values.put(CibaRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
  }

  public CibaRequestContextService get(CibaRequestPattern pattern) {
    CibaRequestContextService cibaRequestContextService = values.get(pattern);
    if (Objects.isNull(cibaRequestContextService)) {
      throw new RuntimeException("unsupported ciba request pattern");
    }
    return cibaRequestContextService;
  }
}
