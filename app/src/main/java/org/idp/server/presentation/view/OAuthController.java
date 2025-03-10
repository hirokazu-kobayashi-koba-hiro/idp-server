package org.idp.server.presentation.view;

import java.util.Map;
import org.idp.server.application.service.tenant.TenantService;
import org.idp.server.application.service.user.internal.UserService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.OAuthApi;
import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.idp.server.presentation.api.ParameterTransformable;
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
  OAuthApi oAuthApi;
  UserService userService;
  TenantService tenantService;

  public OAuthController(
      IdpServerApplication idpServerApplication,
      UserService userService,
      TenantService tenantService) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    this.userService = userService;
    this.tenantService = tenantService;
  }

  @GetMapping("{tenant-id}/v1/authorizations")
  public Object get(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      Model model) {
    Map<String, String[]> params = transform(request);
    Tenant tenant = tenantService.get(tenantId);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());
    OAuthRequestResponse response = oAuthApi.request(oAuthRequest);
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
