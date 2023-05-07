package org.idp.server.ciba;

public class CibaRequestAnalyzer {

  CibaRequestParameters parameters;

  public CibaRequestAnalyzer(CibaRequestParameters parameters) {
    this.parameters = parameters;
  }

  public CibaRequestPattern analyze() {
    if (parameters.hasRequest()) {
      return CibaRequestPattern.REQUEST_OBJECT;
    }
    return CibaRequestPattern.NORMAL;
  }
}
