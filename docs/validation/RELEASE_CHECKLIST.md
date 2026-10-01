# Checklist de release — Hoodie 1.0

Marcar cada item só depois de verificado. Itens automáticos ficam verdes pela CI; os manuais
precisam de evidência (print, log do Developer Lab ou vídeo) anexada ao PR de release.

## Engine
- [ ] Nenhum contexto é alterado sem gerar boundary quando necessário (CT-TRANSITION-*)
- [ ] Hoodie reage imediatamente às mudanças (CT-TRANSITION-006)
- [ ] Reconstrução offline continua determinística (CT-PERSIST)
- [ ] GPS flap não deixa histórico falso (CT-FLAP-*)
- [ ] Virada de dia funciona (CT-DATE-001/002)
- [ ] Mudança de timezone funciona (CT-DATE-003/004/005)

## Localização (manual, `GEOFENCE_SCENARIOS.md`)
- [ ] Foreground permission validada
- [ ] Background permission validada
- [ ] Geofence funciona com app fechado (CT-GEO-007)
- [ ] Geofence funciona após reboot (CT-GEO-006)
- [ ] Limite de geofences tratado
- [ ] Erros aparecem na interface (CT-GEO-008/009)

## Persistência
- [ ] Room migration testada (`Migration1To2Test`, instrumentado)
- [ ] Timeline ligada à origem
- [ ] Banco criptografado (CT-SEC-001..003, instrumentado)
- [ ] Delete Everything remove banco, chaves, geofences, workers e notificações (CT-SEC-004)

## Visual (`VISUAL_ACCEPTANCE.md`)
- [ ] Caminhada frontal / costas / esquerda / direita
- [ ] Transição para cama e sentar/levantar
- [ ] Trabalho, restaurante e academia completos
- [ ] Pixel-perfect sem interpolação
- [ ] Todas as cenas validadas manhã/dia/tarde/noite

## Qualidade
- [ ] `./gradlew testDebugUnitTest` verde
- [ ] `./gradlew lintDebug` verde
- [ ] `./gradlew assembleDebug` verde
- [ ] `./gradlew connectedDebugAndroidTest` verde
- [ ] CI obrigatória na `main` (branch protection: build, unit tests, lint)
- [ ] Nenhum `TODO`/`FIXME` no código (`git grep -nE "TODO|FIXME" -- app/src`)
- [ ] README atualizado com os números reais (animações, cenas, tabelas)

## Jornadas manuais
- [ ] Jornada 1 — primeiro uso (instalação limpa até a Home funcionando)
- [ ] Jornada 6 — reboot sem abrir o app: geofences e worker de volta
      (`adb shell dumpsys jobscheduler | grep hoodie`)

## Homologação
| Aparelho / versão | Jornada 1 | CT-GEO 001–010 | Economia de bateria | Ok |
|---|---|---|---|---|
| Android 10 | ☐ | ☐ | ☐ | ☐ |
| Android 11 | ☐ | ☐ | ☐ | ☐ |
| Android 12 / 12L | ☐ | ☐ | ☐ | ☐ |
| Android 13 | ☐ | ☐ | ☐ | ☐ |
| Android 14 | ☐ | ☐ | ☐ | ☐ |
| Android 15 / 16 | ☐ | ☐ | ☐ | ☐ |
| Samsung real | ☐ | ☐ | ☐ | ☐ |
| Pixel (ou AOSP próximo) | ☐ | ☐ | ☐ | ☐ |
