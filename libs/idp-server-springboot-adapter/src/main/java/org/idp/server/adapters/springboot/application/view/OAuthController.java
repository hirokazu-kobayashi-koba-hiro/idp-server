package org.idp.server.adapters.springboot.application.view;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.oidc.OAuthFlowApi;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("{tenant-id}/view/v1/authorizations")
public class OAuthController implements ParameterTransformable {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthController.class);
  OAuthFlowApi oAuthFlowApi;

  public OAuthController(IdpServerApplication idpServerApplication) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
  }

  @GetMapping
  public Object get(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest,
      Model model) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthRequestResponse response =
        oAuthFlowApi.request(tenantIdentifier, params, requestAttributes);

    switch (response.status()) {
      case OK -> {
        log.info("sessionEnable: false");
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_SESSION_ENABLE -> {
        log.info("sessionEnable: true");
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_ACCOUNT_CREATION -> {
        log.info("request creation account");
        return new RedirectView(
            String.format(
                "/signup/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        log.info("redirect");
        return "redirect:" + response.redirectUri();
      }
      default -> {
        log.warn(
            String.format(
                "error: %s, description: %s", response.error(), response.errorDescription()));

        return new RedirectView(
            String.format(
                "/error/index.html?error=%s&error_description=%s",
                response.error(), response.errorDescription()));
      }
    }
  }

  @PostMapping
  public Object post(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest,
      Model model) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthRequestResponse response =
        oAuthFlowApi.request(tenantIdentifier, params, requestAttributes);

    switch (response.status()) {
      case OK -> {
        log.info("sessionEnable: false");
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_SESSION_ENABLE -> {
        log.info("sessionEnable: true");
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_ACCOUNT_CREATION -> {
        log.info("request creation account");
        return new RedirectView(
            String.format(
                "/signup/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        log.info("redirect");
        return "redirect:" + response.redirectUri();
      }
      default -> {
        log.warn(
            String.format(
                "error: %s, description: %s", response.error(), response.errorDescription()));

        return new RedirectView(
            String.format(
                "/error/index.html?error=%s&error_description=%s",
                response.error(), response.errorDescription()));
      }
    }
  }
}
