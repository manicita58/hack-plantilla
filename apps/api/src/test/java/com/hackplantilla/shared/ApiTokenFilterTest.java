package com.hackplantilla.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackplantilla.shared.web.ApiTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Qué queda protegido y qué no. Sin contexto de Spring: es un filtro, se le
 * pasan requests de mentira y se mira el status.
 */
class ApiTokenFilterTest {

    private static final String TOKEN = "s3cr3t";

    @Test
    void sinTokenConfiguradoNoFiltraNada() throws Exception {
        assertThat(status(new ApiTokenFilter(""), "POST", "/ai/ask", null)).isEqualTo(200);
    }

    @Test
    void protegeAiYLasEscriturasDeGeo() throws Exception {
        ApiTokenFilter filtro = new ApiTokenFilter(TOKEN);

        assertThat(status(filtro, "POST", "/ai/ask", null)).isEqualTo(401);
        assertThat(status(filtro, "GET", "/ai/status", null)).isEqualTo(401);
        assertThat(status(filtro, "DELETE", "/geo/features/1", null)).isEqualTo(401);
        assertThat(status(filtro, "POST", "/geo/features", "otro-token")).isEqualTo(401);
    }

    @Test
    void dejaPasarLoPublicoYLoQueTraeElTokenCorrecto() throws Exception {
        ApiTokenFilter filtro = new ApiTokenFilter(TOKEN);

        // Lectura del mapa, cadena y health siguen abiertos.
        assertThat(status(filtro, "GET", "/geo/features", null)).isEqualTo(200);
        assertThat(status(filtro, "POST", "/ledger", null)).isEqualTo(200);
        assertThat(status(filtro, "GET", "/health", null)).isEqualTo(200);
        // El preflight de CORS no lleva headers propios: bloquearlo rompe el front.
        assertThat(status(filtro, "OPTIONS", "/ai/chat", null)).isEqualTo(200);

        assertThat(status(filtro, "POST", "/ai/ask", TOKEN)).isEqualTo(200);
    }

    private static int status(ApiTokenFilter filtro, String metodo, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, path);
        if (token != null) {
            request.addHeader("X-Api-Token", token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
