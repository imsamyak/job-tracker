package org.example.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.controller.ShutdownController;
import org.example.service.ShutdownService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Using the app cancels a pending shutdown: any request whose path isn't the shutdown endpoint itself
 * cancels it, before the request is otherwise handled.
 */
@Slf4j
public class ShutdownCancelFilter extends OncePerRequestFilter {

    private final ShutdownService shutdownService;

    public ShutdownCancelFilter(ShutdownService shutdownService) {
        this.shutdownService = shutdownService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!ShutdownController.PATH.equals(request.getRequestURI()) && shutdownService.cancelPending()) {
            log.info("Cancelled pending shutdown - {} {} used the app", request.getMethod(), request.getRequestURI());
        }
        chain.doFilter(request, response);
    }
}
