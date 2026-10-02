# Índices e consultas frequentes

Auditoria de `Entities.kt` e `Daos.kt` em 02/10/2026. O banco atual é v6; a v5 adicionou o histórico digital completo e a v6 adicionou mobilidade. Novos índices devem entrar em uma migração 6→7, sem alterar os schemas históricos.

## Índices existentes suficientes

| Tabela | Consulta | Evidência no schema atual |
| --- | --- | --- |
| context_events | Contexto aberto, ordenado por início | Índice composto endedAt/startedAt e índices individuais |
| context_events | Histórico por intervalo de início | Índice startedAt |
| timeline_events | Histórico em intervalo | Índice timestamp |
| timeline_events | Origem type/id | Índice composto sourceType/sourceId |
| daily_screen_hourly | Dia, ordenado por hora | Chave primária date/hour |
| daily_context_usage | Dia e ranking dos contextos | Chave primária date/context; no máximo os contextos do enum por dia |
| phone_app_sessions | Retenção por epochDay | Índice epochDay |
| daily_device_usage | Resumo por dia | Chave primária date |

## Índices adicionais a implementar

| Tabela | Colunas | Motivo |
| --- | --- | --- |
| location_events | placeId, timestamp | Última entrada por lugar |
| hoodie_activities | endedAt, startedAt | Atividade encerrada em um instante, com ordenação por início |
| timeline_events | sourceType, timestamp | Exclusão por tipo e intervalo na reconstrução histórica |
| phone_app_sessions | epochDay, startedAt | Sessões de um dia em ordem cronológica |
| daily_app_usage | date, foregroundMs | Ranking de todos os apps do dia |
| daily_context_app_usage | date, foregroundMs | Ranking por dia sem ordenação temporária |
| daily_phone_timeline | date, startedAt, id | Timeline do dia com desempate por ID |

Manter os índices históricos ao adicionar os novos nesta migração. Avaliar redundâncias somente com uma migração explícita posterior e evidência de uso.

## Verificação necessária

- Migrar um arquivo v6 contendo dados para o schema novo e reabrir com Room.
- Conferir os índices usando os metadados SQLite.
- Verificar os planos das consultas com `EXPLAIN QUERY PLAN` e a ausência de ordenação temporária nas consultas cronológicas e de ranking.
- Executar a cadeia de migrações dos schemas anteriores até a versão atual.
- Reabrir o banco cifrado no Android e preservar o histórico digital e os lugares.

Este documento registra a auditoria e os critérios; os índices adicionais ainda não estão implementados.
