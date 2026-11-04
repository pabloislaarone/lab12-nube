package pe.edu.tecsup.polla.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Agrega la cabecera X-Instance-Id a cada respuesta: así se ve qué réplica atendió
 * la petición y se puede evidenciar el balanceo de carga.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private final String instanceId;

    public InstanceHeaderFilter(AppProperties props) {
        this.instanceId = props.instanceId();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Instance-Id", instanceId);
        chain.doFilter(request, response);
    }
}
