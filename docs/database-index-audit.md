# Índices do banco v7

A versão 6 contém as tabelas de mobilidade; a migração 6→7 acrescenta índices sem reescrever dados ou usar migração destrutiva. As migrações anteriores permanecem registradas em `ALL_MIGRATIONS`.

| Tabela | Índice novo | Consulta frequente |
| --- | --- | --- |
| location_events | placeId, timestamp | Último evento do lugar |
| hoodie_activities | endedAt, startedAt | Atividade encerrada e mais recente |
| timeline_events | sourceType, timestamp | Origem em intervalo de tempo |
| phone_app_sessions | epochDay, startedAt | Sessões ordenadas de um dia |
| daily_app_usage | date, foregroundMs | Ranking completo do dia |
| daily_context_app_usage | date, foregroundMs | Apps por contexto do dia |
| daily_phone_timeline | date, startedAt, id | Timeline estável do dia |

O índice composto `context_events(endedAt, startedAt)` já existe desde a versão 4. `daily_context_usage(date, context)` já usa chave primária composta; não recebe índice redundante para a consulta por data.

`Migration6To7Test` cria um banco v6 a partir do schema exportado, preserva sessões/agregados e usa EXPLAIN QUERY PLAN para verificar os sete índices sem TEMP B-TREE. Também abre schemas 1 a 6 com todas as migrações até a versão atual. A execução da versão reintegrada está pendente; resultados anteriores à restauração são históricos.

## Resultado reintegrado

`Migration6To7Test` passou seus dois testes na suíte completa atual (381 testes, zero falhas): dados v6 preservados, sete consultas usando os índices previstos sem TEMP B-TREE e schemas 1–6 abertos até v7 sem fallback destrutivo. `Migration5To6Test` também passou preservando perguntas antigas e mobilidade ao chegar à versão atual. A validação SQLCipher no Android continua pendente.
