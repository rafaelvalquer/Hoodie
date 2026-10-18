# HOODIE-BACKUP-V1 — cobertura Room v12

| Entidade | Tratamento |
|---|---|
| `places` | Exportada; coordenadas decifradas em memória e recifradas no destino |
| `routines` | Exportada |
| `day_exceptions` | Exportada |
| `location_events` | Exportada |
| `context_events` | Exportada; intervalos abertos encerrados no corte do snapshot |
| `context_confirmations` | Exportada |
| `context_questions` | Excluída: estado temporário |
| `hoodie_state` | Reconstruída no destino |
| `hoodie_activities` | Exportada |
| `timeline_events` | Exportada |
| `memories` | Exportada |
| `daily_device_usage` | Exportada, conforme opção de histórico digital |
| `daily_app_usage` | Exportada, conforme opção de histórico digital |
| `daily_context_app_usage` | Exportada, conforme opção de histórico digital |
| `app_category_overrides` | Exportada, conforme opção de histórico digital |
| `phone_app_sessions` | Exportada, conforme opção de histórico digital |
| `daily_screen_hourly` | Exportada, conforme opção de histórico digital |
| `daily_context_usage` | Exportada, conforme opção de histórico digital |
| `daily_phone_timeline` | Exportada, conforme opção de histórico digital |
| `mobility_sessions` | Exportada; intervalos abertos encerrados no corte do snapshot |
| `mobility_segments` | Exportada; intervalos abertos encerrados no corte do snapshot |
| `day_state` | Reconstruída no destino |
| `diary_corrections` | Exportada |
| `learned_routine_slots` | Exportada |
| `transport_patterns` | Exportada |
| `transport_evidence` | Exportada; intervalos abertos encerrados no corte do snapshot |
| `effective_routine_versions` | Exportada |
| `correction_undo` | Excluída: estado temporário |
| `notification_delivery` | Excluída: estado temporário |

Perguntas são temporárias; respostas confirmadas permanecem em context_confirmations e diary_corrections.
TransportEvidence preserva agregados e descarta reservas/IDs de WorkManager. O estado da mobilidade vira ARRIVED.
Os campos dependentes de chave local são places.encryptedCoordinates e context_questions.encryptedCoordinates; o segundo não é exportado.

O contrato é lógico e congelado em BackupSchemaV1/BackupModelsV1. Não serializa o arquivo SQLite nem lê seu schema como contrato em runtime. Novas colunas exigem revisão explícita do adaptador e do teste de cobertura.
