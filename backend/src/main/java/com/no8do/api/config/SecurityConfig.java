package com.no8do.api.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração MÍNIMA e TEMPORÁRIA de segurança.
 *
 * O Spring Security foi incluído no projeto conforme decisão de stack,
 * mas o fluxo completo de autenticação/autorização ainda não foi
 * implementado nesta fase. Por enquanto todas as requisições são
 * liberadas (permitAll) apenas para permitir o desenvolvimento local
 * do frontend e do backend sem bloqueios.
 *
 * TODO: substituir por um fluxo real de autenticação (ex.: login,
 * JWT/sessão, controle de acesso por rota) em uma etapa futura.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    /**
     * Libera o frontend local (Vite, porta 5173) para chamar a API
     * durante o desenvolvimento. Ajustar/restringir quando houver
     * ambientes adicionais.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
