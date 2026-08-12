-- Migration inicial (baseline) do No8do.
--
-- Nesta etapa nenhuma funcionalidade de negócio foi implementada ainda,
-- então esta migration existe apenas para validar que o pipeline do
-- Flyway está corretamente conectado ao PostgreSQL local.
--
-- As próximas migrations (V2__..., V3__..., etc.) vão introduzir as
-- tabelas reais do domínio (projetos, ideias, clientes, pendências,
-- links, decisões, etc.).

select 1;
