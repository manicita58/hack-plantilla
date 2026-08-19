package com.hackplantilla.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token compartido para los endpoints que gastan plata (todo `/ai`) o que
 * escriben en el mapa (`/geo` con POST, PUT, PATCH o DELETE).
 *
 * Apagado por defecto: sin `API_TOKEN` en el .env no filtra nada, que es lo que
 * querés en local y en una demo abierta. Con token, esos endpoints piden el
 * header `X-Api-Token`.
 *
 * OJO con qué protege esto: un token que viaja en un front público NO es un
 * secreto, cualquiera lo lee en el devtools. Sirve para que no te encuentre un
 * bot y te queme los créditos, no para autenticar usuarios. Para eso hace falta
 * login de verdad; y para el abuso desde el browser, el rate limit por IP que
 * está en las labels de Traefik.
 */
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Token";
    private static final Set<String> ESCRITURAS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final byte[] token;

    public ApiTokenFilter(@Value("${app.api-token:}") String token) {
        this.token = token == null ? new byte[0] : token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (token.length == 0 || "OPTIONS".equals(request.getMethod())) {
            return true;   // sin token configurado, o preflight de CORS: pasa
        }
        String path = request.getRequestURI();
        boolean cuestaPlata = path.startsWith("/ai");
        boolean escribeElMapa = path.startsWith("/geo") && ESCRITURAS.contains(request.getMethod());
        return !(cuestaPlata || escribeElMapa);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        // isEqual y no equals(): comparación en tiempo constante, para no filtrar
        // el token carácter a carácter midiendo cuánto tarda en responder.
        if (recibido != null && MessageDigest.isEqual(token, recibido.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"falta o no coincide el header " + HEADER + "\"}");
    }
}
