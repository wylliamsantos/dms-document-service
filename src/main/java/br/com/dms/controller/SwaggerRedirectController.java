package br.com.dms.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SwaggerRedirectController {

    private static final String SWAGGER_UI_PATH = "/swagger-ui/index.html";
    private final boolean redirectEnabled;

    public SwaggerRedirectController(@Value("${dms.security.redirect-root-to-swagger-enabled:true}") boolean redirectEnabled) {
        this.redirectEnabled = redirectEnabled;
    }

    @GetMapping("/")
    public String redirectRoot() {
        if (!redirectEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:" + SWAGGER_UI_PATH;
    }
}
