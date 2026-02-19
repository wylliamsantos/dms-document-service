package br.com.dms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwaggerRedirectControllerTest {

    @Test
    void shouldRedirectToSwaggerWhenEnabled() {
        SwaggerRedirectController controller = new SwaggerRedirectController(true);

        String response = controller.redirectRoot();

        assertThat(response).isEqualTo("redirect:/swagger-ui/index.html");
    }

    @Test
    void shouldReturnNotFoundWhenRedirectIsDisabled() {
        SwaggerRedirectController controller = new SwaggerRedirectController(false);

        assertThatThrownBy(controller::redirectRoot)
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }
}
