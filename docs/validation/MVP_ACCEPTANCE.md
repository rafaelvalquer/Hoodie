# Aceite do MVP

O MVP está **funcionalmente fechado** quando P0 e P1 (fases 0–5 do plano de hardening) estão verdes.
Nenhuma funcionalidade nova entra antes disso.

## Critérios

| Área | Critério | Como verificar |
|---|---|---|
| Engine | Nenhum contexto muda de tipo sem boundary | `ContextTransitionServiceTest` (CT-TRANSITION-*) |
| Engine | Hoodie reage na mesma reconciliação | CT-TRANSITION-006 |
| Engine | Reconstrução offline determinística | `SimulatorPersistenceTest` (CT-PERSIST) |
| Engine | GPS flap não deixa histórico falso | CT-FLAP-* |
| Tempo | Virada de dia, fuso, `TIME_SET`, DST | CT-DATE-* |
| Localização | Fluxo foreground → background separado | `LocationPermissionStateTest` + roteiro manual em `GEOFENCE_SCENARIOS.md` |
| Localização | Limite de geofences e prioridade | `GeofenceSelectionPolicyTest` |
| Localização | Erros visíveis em Ajustes | `GeofenceRegistrationResult` → card "Localização" |
| Persistência | Timeline ligada à origem | `TimelineRepositoryTest`, migration 1→2 |
| Qualidade | `testDebugUnitTest`, `lintDebug`, `assembleDebug` verdes | `.github/workflows/android-ci.yml` |

## Jornadas ponta a ponta

As jornadas 2–5 rodam automaticamente em `EndToEndJourneysTest` (Robolectric + Room em memória).
As jornadas 1 e 6 dependem de interação/hardware e seguem o roteiro manual em `RELEASE_CHECKLIST.md`.

1. **Primeiro uso** — instala → onboarding → nome → permissões → casa → trabalho → rotina → Home.
2. **Dia completo** — HOME → EXIT → COMMUTING → WORK → LUNCH → WORK → COMMUTING → HOME → SLEEP.
3. **App fechado** — fecha 08:00, sai 08:30, chega 09:00, abre 10:30 → "🏢 Trabalho desde ~09:00" e Hoodie trabalhando.
4. **GPS flap** — EXIT HOME, 2 min, ENTER HOME → continua HOME, sem timeline/notificação falsa.
5. **Sem localização** — permissão removida → app não quebra, rotina provável, Hoodie continua vivendo.
6. **Reboot** — reboot sem abrir o app → geofences registrados e worker agendado.
