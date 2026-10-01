# Cenários oficiais de contexto

Referência de comportamento do Context Engine. Todo teste novo (unitário, de integração ou manual)
deve reproduzir esta sequência ou derivar dela. Data de referência dos testes: **segunda-feira
2026-10-05**, fuso `America/Sao_Paulo`, rotina `OFFICE` 08:30–17:30, almoço 12:00–13:00.

## Dia de referência (CT-001 … CT-008)

| ID | Hora | Sinal | Contexto esperado | Hoodie esperado |
|---|---|---|---|---|
| CT-001 | 07:00 | — (em casa desde a noite anterior) | `HOME` | acordando (`WAKING_UP`/`SLEEPING` → rotina da manhã) |
| CT-002 | 08:03 | `EXIT` Casa | `COMMUTING` | `COMMUTING` imediatamente |
| CT-003 | 08:41 | `ENTER` Trabalho | `WORK` | atividade de trabalho (`WORKING`, `COFFEE`…) |
| CT-004 | 12:05 | `EXIT` Trabalho | `COMMUTING` (almoço candidato, checagem em +15 min) | `COMMUTING` |
| CT-005 | 12:20 | checagem de almoço / almoço confirmado | `LUNCH` **imediatamente** (novo evento) | `EATING`/`COFFEE`/`PHONE` |
| CT-006 | 12:55 | `ENTER` Trabalho | `WORK` | trabalho |
| CT-007 | 17:40 | `EXIT` Trabalho | `COMMUTING` | `COMMUTING` |
| CT-008 | 18:20 | `ENTER` Casa | `HOME` | atividade de casa; à noite `SLEEPING` |

Automatizado em: `ContextEngineIntegrationTest` (`CT-001..CT-008 dia de referencia`) e
`SimulatorPersistenceTest` (`dia completo`).

## Transições (Fase 1 — `ContextTransitionService`)

Regra: só existem duas operações — **manter** o contexto atual ou **fechar o atual + abrir outro**.
Mudar o tipo de um evento já aberto só acontece em correção histórica explícita
(`ContextTransitionService.revertFlap`).

| ID | Situação | Resultado |
|---|---|---|
| CT-TRANSITION-001 | `COMMUTING` (12:05) → almoço confirmado (12:20) | dois eventos: `COMMUTING 12:05–12:20`, `LUNCH 12:20–` |
| CT-TRANSITION-002 | `WORK` + resposta "Não" | boundary: `WORK` fechado, `LEISURE` aberto no instante da resposta |
| CT-TRANSITION-003 | `HOME` → manual `GYM` | boundary |
| CT-TRANSITION-004 | mesmo contexto de novo | nenhum evento duplicado |
| CT-TRANSITION-005 | mesmo lugar + mesmo contexto | evento atual preservado (mesmo id) |
| CT-TRANSITION-006 | qualquer boundary novo | `hoodie_state.userContext` passa a refletir o novo contexto na mesma reconciliação |

Aceite: depois de "12:20 usuário confirma LUNCH" é impossível o estado persistido conter
`userContext = COMMUTING`.

## Linha do tempo e oscilação de GPS (Fase 3)

| ID | Situação | Resultado |
|---|---|---|
| CT-FLAP-001 | `EXIT` Casa 08:15 → `ENTER` Casa 08:18 | continua `HOME` (mesmo evento reaberto), sem `COMMUTING` |
| CT-FLAP-002 | idem | nenhuma linha "Saiu de: Casa" na timeline; nenhuma atividade do Hoodie com `userContext = COMMUTING` |
| CT-FLAP-003 | `EXIT` 08:15 → `ENTER` 08:25 (> 5 min) | deslocamento real: dois boundaries |

## Tempo (Fase 4)

| ID | Situação | Resultado |
|---|---|---|
| CT-DATE-001 | 23:59 → 00:00 | `dayOff` observado passa para o novo dia |
| CT-DATE-002 | domingo → segunda | `isWorkDay` recalculado |
| CT-DATE-003 | mudança de fuso | rotina recalculada no fuso novo |
| CT-DATE-004 | `TIME_SET` | reconciliação imediata (`reconcileNow`) |
| CT-DATE-005 | horário de verão | reconstrução consistente |
| CT-RECOVERY-001 | app sem rodar > 3 dias | `RESET_AFTER_LONG_ABSENCE` + "🐱 Hoodie retomou a rotina." sem histórico inventado |
