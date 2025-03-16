package org.idp.server.adapters.springboot.view;

import java.util.Map;

import org.idp.server.core.function.OAuthFlowFunction;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.type.extension.Pairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping
public class OAuthController implements ParameterTransformable {

  Logger log = LoggerFactory.getLogger(OAuthController.class);
  OAuthFlowFunction oAuthFlowFunction;

  public OAuthController(
      IdpServerApplication idpServerApplication) {
    this.oAuthFlowFunction = idpServerApplication.oAuthFlowFunction();
  }

  @GetMapping("{tenant-id}/v1/authorizations")
  public Object get(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      Model model) {
    Map<String, String[]> params = transform(request);

    Pairs<Tenant, OAuthRequestResponse> result = oAuthFlowFunction.request(tenantIdentifier, params);
    Tenant tenant = result.getLeft();
    OAuthRequestResponse response = result.getRight();

    switch (response.status()) {
      case OK -> {
        log.info("sessionEnable: false");
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenant.identifierValue()));
      }
      case OK_SESSION_ENABLE -> {
        log.info("sessionEnable: true");
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenant.identifierValue()));
      }
      case OK_ACCOUNT_CREATION -> {
        log.info("request creation account");
        return new RedirectView(
            String.format(
                "/signup/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenant.identifierValue()));
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        log.info("redirect");
        return "redirect:" + response.redirectUri();
      }
      default -> {
        model.addAttribute("error", response.error());
        model.addAttribute("errorDescription", response.errorDescription());
        return "error";
      }
    }
  }
}
