# Cenários de geofence (aparelho / emulador real)

Geofencing depende do Google Play Services e do sistema; por isso estes cenários são **manuais**
e precisam ser repetidos em cada aparelho da homologação (ver `RELEASE_CHECKLIST.md`).
A lógica que reage aos eventos (Context Engine) já é coberta por testes automáticos.

## Preparação

```bash
# Build de debug instalado sem apagar dados
./gradlew :app:installDebug
ADB=adb   # ou $ANDROID_HOME/platform-tools/adb

# Ver geofences registrados pelo Play Services
$ADB shell dumpsys activity service com.google.android.gms/.location.reporting.service.ReportingAndroidService | head
# Log de eventos do app (build debug): Ajustes → Developer Lab → aba CONTEXT/GEOFENCE
```

No emulador, a posição é simulada com `adb emu geo fix <longitude> <latitude>`. Use coordenadas
a > 500 m do lugar para "sair" e o centro do lugar para "entrar". No aparelho físico use um app de
mock location (Opções do desenvolvedor → app de local fictício) ou desloque-se de fato.

Pré-condições comuns: onboarding concluído, Casa e Trabalho cadastrados, permissão
"Permitir o tempo todo" concedida, Ajustes mostrando `✅ N locais monitorados`.

## Roteiro

| ID | Passos | Esperado |
|---|---|---|
| CT-GEO-001 HOME ENTER | Fora de casa → `geo fix` no centro da Casa | ≤ 2 min: contexto `HOME`, notificação "Vocês estão de volta em casa" (se fora ≥ 30 min) |
| CT-GEO-002 HOME EXIT | Em casa → `geo fix` a 1 km | contexto `COMMUTING`, timeline "Saiu de: Casa" |
| CT-GEO-003 WORK ENTER | Em deslocamento → centro do Trabalho | contexto `WORK`, notificação "chegou ao trabalho" |
| CT-GEO-004 WORK EXIT | No trabalho, 12:05 → sair | `COMMUTING`; após 15 min fora → `LUNCH` (novo evento) |
| CT-GEO-005 DWELL | Ficar 5 min dentro de um lugar sem ENTER prévio | DWELL aplica o contexto do lugar uma única vez |
| CT-GEO-006 reboot | `adb reboot`, **não** abrir o app, entrar/sair de um lugar | evento processado; Developer Lab mostra registro após `BOOT_COMPLETED` |
| CT-GEO-007 app morto | `adb shell am kill com.hoodie.app` (ou remover dos recentes) → entrar/sair | evento processado com o processo morto |
| CT-GEO-008 localização desligada | Desligar a localização do sistema | Ajustes: `⚠️ Geofences pausados — localização desligada`; Hoodie segue rotina provável; ao religar, reconciliação re-registra |
| CT-GEO-009 background removida | Configurações → Permissões → Localização → "Só durante o uso" | Ajustes: `⚠️ Localização em segundo plano desativada`; Home mostra modo rotina provável |
| CT-GEO-010 atualização | Instalar nova versão por cima (`installDebug`) | `MY_PACKAGE_REPLACED` re-registra geofences sem abrir o app |

## Verificação de limite

`GeofenceSelectionPolicy` monitora no máximo **95** lugares (limite do sistema: 100 por app),
priorizando Casa → Trabalho → Academia → favoritos → mais confirmados → visitados mais recentemente.
Coberto por `GeofenceSelectionPolicyTest`; manualmente, cadastrar > 95 lugares e conferir em Ajustes
`✅ 95 locais monitorados · N fora do limite`.
