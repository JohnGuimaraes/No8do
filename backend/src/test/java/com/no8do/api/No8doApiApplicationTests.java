package com.no8do.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste padrão de carregamento de contexto.
 *
 * Requer o PostgreSQL local rodando (docker compose up -d), já que o
 * datasource e o Flyway são inicializados junto com o contexto Spring.
 */
@SpringBootTest
class No8doApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
