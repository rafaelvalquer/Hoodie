# Integração com a história canônica

Base da análise: `d21cfa43`, banco v7. Esta nota registra os pontos de integração que orientaram a implementação. O banco atual é v8; implementação, flags e resultados de validação estão no [registro de progresso](day-intelligence-progress.md).

## Confiança e decisões

`ContextScorer` produz candidatos consumidos por `GeofenceContextHandler`. Seu comportamento atual (40–84 aplica e pergunta) precisa passar a distinguir UNKNOWN, ASK_USER, PROVISIONAL e AUTO_ACCEPT. Almoço de saída e checagem posterior precisam usar a mesma decisão. `MobilityConfidenceScorer` mantém a proteção contra movimento dentro de um lugar conhecido; `MobilityEngine.evaluate` não deve forçar uma pergunta de primeira viagem quando a nova decisão já aceita automaticamente.

Perguntas são persistidas em `context_questions`. A política nova consulta esse histórico, incluindo o id da sessão, para sobreviver à morte do processo. Uma resposta explícita deve impedir uma nova pergunta sobre o mesmo trecho. Cooldown não pode depender apenas de estado em memória.

## Estado do dia

`DailyActivityWindowResolver` já combina ContextWakeEvidenceBuilder, PhoneWakeEvidenceBuilder, MobilityWakeEvidenceBuilder, WakeDetector, RealUserActivityBuilder e SleepOnsetDetector. O coordinator deve consumir esses resultados; não criar outro detector de sono/despertar. O snapshot atual é persistido em `day_state`, enquanto a história continua sendo derivada das fontes existentes.

O coordinator pode observar as invalidações de Room e um tick temporal para inatividade. A publicação ocorre após persistência. Eventos atrasados não podem regredir o snapshot; ACTIVE não volta a WAKING porque um horário previsto mudou.

## Correção e projeções

`ContextTransitionService` escreve boundaries do presente. Correção histórica é uma operação distinta em `TransactionRunner`, com auditoria original, atualização de contextos/trechos e timeline relacionada. Uma edição de horário precisa validar os vizinhos e preservar uma sequência sem sobreposição.

`DiaryRepository.loadDiary` deriva Diário, que alimenta Jornada e Relógio. `DiaryViewModel` mantém um cache de dias anteriores, portanto uma correção precisa invalidá-lo e observar a alteração canônica; recarregar só o banco não basta.

`DeviceUsageRepository.refreshDay` consulta Android e pode conservar totais históricos antigos. O recálculo após correção precisa usar `phone_app_sessions` já armazenadas e recalcular somente a distribuição por contexto, sem exigir permissão nova, apagar sessões ou reduzir o tempo total de tela.

## Aprendizado e transporte

`RoutineEntity` continua sendo configuração manual. A rotina aprendida ocupa sua própria tabela; nenhum worker escreve horários manuais. Exceções vêm de `DayExceptionDao.range`.

`MobilityEngine` já fecha/abre segmentos em `newSegment` e possui respostas de modo/chegada. O classificador novo deve preservar caminhada→veículo→caminhada e armazenar apenas agregados. O histórico de padrões é indexado por origem, destino, grupo de dia, faixa horária e modo, incluindo rejeições. Confirmações e correções vencem resultados do classificador.

## Banco e inicialização

`DatabaseModule` e `ALL_MIGRATIONS` registram a migração aditiva 7→8. Os schemas anteriores permanecem intactos. Novas tabelas: `day_state`, `diary_corrections`, `learned_routine_slots`, `transport_patterns`.

`HoodieApp` é a entrada real da aplicação; `WorkScheduler` registra os trabalhos recorrentes. Inicialização do coordinator deve respeitar o gate de disponibilidade do banco e as flags. Rollout: confiança, estado do dia, correções, rotina, transporte.
