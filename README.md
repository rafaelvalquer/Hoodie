# HOODIE — Seu companheiro de rotina

```
      /\_/\
     ( o.o )   Você vive a sua rotina.
      > ^ <    O Hoodie vive uma rotina paralela.
```

**Versão: 0.2.0-dev** (Hoodie V0.2 — Visual & Diary Foundation; versão definida em `gradle.properties`).

App Android **100% nativo (Kotlin + Jetpack Compose)**, **offline** e com **todo o histórico só no aparelho**.
A internet é usada apenas na tela de buscar um endereço no mapa (veja Privacidade).

## Como rodar

Requisitos: JDK 17+ (testado com JDK 21) e Android SDK 36.

```bash
./gradlew :app:assembleDebug        # APK em app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # testes de regra (CT-*), persistência e assets
```

Revisão visual sem emulador: os testes `SpritePreviewTest` e `ScenePreviewTest` exportam PNGs para
`app/build/pixel-preview/` (model sheet do Hoodie e o *vertical slice* casa → rua → trabalho → almoço → casa).
O ícone do app é gerado a partir do próprio sprite: `./gradlew :app:testDebugUnitTest --tests '*IconExportTest*' -PexportIcons=true`.

## Arquitetura

```
 Sensores (geofence, hora, rotina, confirmações)
            │
            ▼
   ContextEngine ──► context_events (fonte única: "onde a pessoa provavelmente está")
            │
            ▼
   HoodieEngine ──► HoodieSimulator (puro) ──► hoodie_state / hoodie_activities / timeline
            │
            ▼
   VisualDirector ──► AnimationStateMachine ──► SceneRenderer (240×320, escala inteira, sem filtro)
            │
            ▼
            🐱
```

| Camada | Pacote | Papel |
|---|---|---|
| Modelo/tempo | `core.model`, `core.time` | `UserContextType`, `HoodieActivity`, `ClockProvider`, `DayPeriod`, janelas de horário |
| Persistência | `core.database`, `core.datastore`, `data.repository` | Room (15 tabelas, migrações versionadas) + DataStore; timeline ligada à origem (`TimelineRepository`) |
| Segurança | `core.security` | Banco inteiro cifrado com SQLCipher (senha aleatória embrulhada por chave do Android Keystore) + coordenadas cifradas (AES‑256‑GCM) |
| Sensores | `core.location`, `core.geofence`, `receiver` | Permissão em etapas (`LocationPermissionState`), até 95 geofences priorizados, erros visíveis, reboot/fuso/hora |
| Regras puras | `engine.context.ContextScorer`, `ConfirmationPolicy`, `engine.routine`, `engine.hoodie.HoodieDecisionEngine`, `NeedsEngine`, `HoodieSimulator` | Sem Android: 100% testáveis |
| Orquestração | `engine.context.ContextEngine`, `ContextTransitionService`, `engine.hoodie.HoodieEngine`, `engine.memory`, `engine.dialogue` | Aplica regras ao banco (só por boundaries), perguntas, notificações |
| Background | `worker` | Reconciliação a cada 15 min, checagem de almoço (+15 min) e de deslocamento longo (+40 min) |
| Pixel engine | `pixel.*` | Sprite procedural ou sprite sheet, 84 animações, 9 cenas, iluminação, partículas, transições |
| UI | `presentation.*` | MVVM com Hilt, Navigation Compose, Material 3 |
| Diário Digital | `core.deviceusage`, `engine.deviceusage`, `domain.phoneinsights`, `pixel.phoneinsights`, `presentation.screens.phoneinsights` | Uso do celular (UsageStatsManager) → sessões → agregados por dia, cruzados com os contextos |

### O princípio mais importante

Nada roda "contando o tempo". O estado do gato é `startedAt` + `expectedEndAt` + necessidades em `needsAt`.
Ao abrir o app (ou quando o worker roda), `HoodieSimulator.advance()` **reconstrói** tudo o que aconteceu no
intervalo, usando seeds derivadas do próprio instante. Abrir às 10:30 ou ter o worker rodando a cada 15 minutos
produz **exatamente o mesmo estado** (teste `CT-PERSIST`).

### Context Engine (sem IA)

Confiança determinística: lugar conhecido +40, horário esperado +30, dia esperado +20, histórico +10..30.
`≥ 85` aplica · `40–84` aplica e pergunta · `< 40` assume `UNKNOWN`.
Perguntas: no máximo 4 por dia e nunca o mesmo contexto em menos de 60 min. Três almoços confirmados
num horário parecido → classificação automática, sem perguntar.
GPS oscilando na borda (saída + volta em < 5 min) desfaz a saída. Sem localização: **rotina provável**.

### Motor visual

```
HoodieActivity → VisualDirector → AnimationStateMachine → AnimationId + Direction
                                                              ↓
                                   SpriteProvider ─┬─ SpriteSheetProvider (Aseprite, quando existir)
                                                   └─ ProceduralSpriteProvider (fallback/debug)
                                                              ↓
                                                        SceneRenderer
```

* **Sprites**: `SpriteProvider` entrega `SpriteFrame` (imagem + âncoras `feet/head/right_hand/left_hand/back` +
  duração + eventos). Sprite sheets do Aseprite em `app/src/main/assets/pixel/hoodie/` substituem clips um a um;
  o resto vem do pintor procedural. Pipeline, camadas do sprite master e exportação: [assets-source/hoodie/README.md](assets-source/hoodie/README.md).
* **Direção**: frente, costas e lado (RIGHT = espelho de LEFT). Caminhada de 8 poses com tempo por frame,
  cabeça e mochila com 1 frame de atraso e cordões do moletom em follow-through.
* **Clips** (`AnimationClip`): ~90 animações com duração por frame, `InterruptPolicy`
  (IMMEDIATE / FINISH_FRAME / FINISH_CYCLE / PLAY_EXIT) e eventos (`SIT`, `MUG_PICKUP`, `FOOD_SERVED`, `FOOTSTEP`…)
  que sincronizam props: a caneca some da mesa quando ele a pega, a comida aparece depois do `WAIT_FOOD`,
  a cadeira mostra se está ocupada, a porta abre e fecha em 4 frames.
* **Transições**: cada troca de estado vira um roteiro — exit da microação → levantar → antecipação →
  andar → abrir porta → fade → entrar → sentar → loop. Dormir/acordar, restaurante, academia,
  café, celular e videogame têm sequências completas.
* **Personalidade**: 6 estados de orelha, piscadas meio-fechado/fechado a cada 3–8 s (às vezes duplas),
  roteiros de olhar por atividade (monitor → teclado → mouse), 5 variações de idle, alongamento a cada 2–6 min,
  café cansado/feliz conforme energia e humor, e reações ao abrir o app (55% segue, 15% olha, 10% acena…).
* Pintor procedural em 48×72 com contorno de 1 px por forma e paleta de 17 cores (validado em teste).
* Cenas em camadas com ordenação por Y (o gato passa atrás da mesa, do cobertor ou do sofá), janela dinâmica
  com céu por período, relógio de parede com a hora real, microanimações (TV, monitor com código, ventilador, esteira, carros, passarinhos).
* Iluminação por overlay (manhã, dia, entardecer, noite) com áreas emissivas e halos de lâmpada em degraus.
* **Pixel Lab**: ferramenta de animação com animação × direção × postura × expressão × velocidade,
  avanço frame a frame, onion-skin, âncoras, bounding box e linha dos pés; laboratório de cena com transições;
  galerias de animações e cenas; e a fonte de cada sprite (sheet ou procedural).

## Diário Digital (Phone Insights)

O Diário passa a contar três camadas do mesmo dia: **vida real** (contextos e lugares) + **vida digital** (celular) + **vida do Hoodie**.

```
ANDROID USAGE STATS ─► UsageStatsSource (só foreground/background, tela, bloqueio)
        ▼
AppSessionBuilder ─► ScreenSessionBuilder ─► AppCategoryResolver ─► ContextUsageCorrelator ─► DailyPhoneUsageCalculator
        ▼                                                                 (PhoneInsightsAssembler, puro)
DeviceUsageRepository ─► Room (só agregados por dia) ─► DiaryDigitalMerger ─► Diário (aba Geral + aba Digital)
```

* **Permissão**: `PACKAGE_USAGE_STATS` é ligada pelo usuário em *Acesso ao uso*. Antes, a tela "Análise do celular" explica
  o que é visto (tempo de tela, apps, tempo por app, sessões, desbloqueios) e o que nunca é (mensagens, texto, fotos, conteúdo da tela).
* **Cálculo**: sessões de tela por `SCREEN_INTERACTIVE → NON_INTERACTIVE`; desbloqueios por `KEYGUARD_HIDDEN`.
  Aparelhos sem esses eventos (API 26–27, alguns fabricantes) caem em estimativa a partir do uso de apps e a UI marca "≈".
  Launchers e a interface do sistema contam como tela ligada, não como app usado.
* **Categorias**: escolha do usuário → mapa interno (YouTube → Vídeo, Spotify → Música, Teams → Trabalho...) → `ApplicationInfo.category` → Outros.
* **Persistência**: `daily_device_usage`, `daily_app_usage`, `daily_context_app_usage`, `app_category_overrides` (migração 2→3).
  Eventos brutos nunca são salvos. O Android só guarda eventos por alguns dias: um recálculo "menor" de um dia antigo não sobrescreve o histórico.
* **Atualização**: ao abrir o Diário/aba Digital, ao trocar a data, ao voltar das configurações e pelo `PhoneInsightsWorker` (a cada 3 h, hoje + ontem).
* **UI retrô**: painéis HUD com scanlines, barras de RPG em blocos, histograma por hora em degraus, ícones reais dos apps
  (`PackageManager`) emoldurados em `AppBadge` (ícone genérico em pixel art quando o app sumiu), e o Hoodie reagindo ao dia digital.
* **Ajustes**: ativar análise · mostrar no Diário · salvar histórico · top apps por contexto · apagar histórico digital.

## Telas do MVP

Splash · Onboarding (nome, permissões, casa e trabalho por GPS ou endereço no mapa, horários, dias) · Escolher lugar no mapa (busca de endereço + pino + raio) · Home/Hoodie · Confirmação de contexto (card + notificação com Sim/Não) ·
Histórico (Hoje / Ontem / 7 dias, com resumo "Seu dia" e "Hoodie") · Lugares · Rotina · Memórias · Perfil do Hoodie · Ajustes · Pixel Lab.

## Privacidade

* Sem conta, servidor, Firebase ou nuvem; backup em nuvem desativado (`data_extraction_rules`).
* Internet só na tela **Buscar endereço no mapa** (onboarding, Lugares e Home): o texto digitado vai para o Geocoder do
  Android e os tiles vêm do OpenStreetMap (osmdroid, cache no armazenamento interno). Rotina, geofences, gato e histórico seguem offline.
* Guardado: lugares (coordenadas cifradas), horários, contextos, histórico, memórias e estado do gato.
* **Não** guardado: trajeto GPS ou posição contínua. Eventos de geofence guardam só `lugar + transição + hora`.
* Diário digital (opcional): só **app + tempo** agregados por dia. Nunca mensagens, texto digitado, fotos ou conteúdo da tela.
* "Apagar todos os dados" limpa banco, preferências, geofences e tarefas; "Apagar histórico digital" limpa só a camada do celular.

## Testes (JVM)

| ID | Onde |
|---|---|
| CT-CONTEXT-001/002/003 | `ContextEngineRulesTest` |
| CT-HOODIE-001/002/003 | `HoodieEngineRulesTest` |
| CT-PERSIST (fechar/reabrir) + dia completo + horário de verão | `SimulatorPersistenceTest` |
| CT-GEO (distância/parsing) | `MiscEngineTest` |
| Assets: 48×72, âncoras, pés sem deslizar, todas as direções, rosto, paleta, idle sem tremer, cenas/portas | `AssetValidationTest` |
| Transições, InterruptPolicy, eventos (caneca/comida/cadeira), dormir/acordar, reações, piscadas | `AnimationBehaviorTest` |
| Aseprite JSON → SpriteSheetProvider ≡ procedural, fallback, relatório de erros, baseline | `SpriteSheetPipelineTest` |
| Diário Digital: sessões de tela/app, categorias, contexto, agregados, timeline digital | `ScreenSessionBuilderTest`, `AppSessionBuilderTest`, `AppCategoryResolverTest`, `ContextUsageCorrelatorTest`, `DailyPhoneUsageCalculatorTest` |
| Diário Digital: integração com o Diário, reações do Hoodie, estados da tela | `PhoneDiaryIntegrationTest` |
| Diário Digital: Room (salvar/reler, override, histórico desligado, apagar) e migração 2→3 | `DeviceUsageRepositoryTest`, `Migration2To3Test` |

CT-GEO-001…005 em aparelho real (entrar/sair/reboot) dependem de dispositivo/emulador com rota simulada.

## Próximos passos sugeridos

1. Validar geofences em aparelho real (ou emulador com rota GPX) e calibrar raios/loitering.
2. Substituir a arte procedural por sprite sheets do Aseprite mantendo o mesmo `AnimationId`/`HoodiePose` como contrato.
3. Criptografia integral do banco (SQLCipher) — a camada já isola os dados sensíveis.
4. Pixel font própria (OFL) para títulos.

## Hardening 1.0

Regras que valem para qualquer mudança nova (documentação completa em `docs/validation/`):

* **Contexto só muda por boundary.** Toda escrita em `context_events` passa pelo `ContextTransitionService`:
  manter o contexto atual ou fechar o atual e abrir outro. Nunca se reescreve o tipo de um evento aberto —
  a única exceção é a correção histórica `revertFlap` (GPS oscilando na borda).
* **Hoodie reage na hora.** Se o contexto mudou "por trás" do estado salvo (evento atrasado, correção), o
  `HoodieSimulator` decide de novo imediatamente.
* **Timeline reconciliável.** Cada linha guarda `sourceType` + `sourceId`; quando a origem é desfeita, a linha some
  na mesma transação (`TransactionRunner`).
* **Tempo.** Telas observam `currentDateFlow` (meia-noite, fuso, `TIME_SET`); `TIME_SET`/`TIMEZONE_CHANGED`
  disparam `reconcileNow()`; ausência > 3 dias → `RESET_AFTER_LONG_ABSENCE` sem inventar histórico.
* **Números de comportamento** ficam em `core.config.HoodieConfig`.
* **Developer Lab** (Ajustes → 🧪, só debug): abas PIXEL, CONTEXT, GEOFENCE, SIMULATOR (+15 min / +1 h / +6 h / +1 dia),
  DATABASE e LOG (`DebugEventLogger`).

### Testes

```bash
./gradlew :app:testDebugUnitTest          # regras puras + integração (Robolectric + Room em memória)
./gradlew :app:connectedDebugAndroidTest  # migração, SQLCipher (CT-SEC), WorkManager, UI (emulador/aparelho)
./gradlew :app:lintDebug :app:assembleDebug
```

A CI (`.github/workflows/android-ci.yml`) roda compile → unit tests → lint → assembleDebug e os testes
instrumentados num emulador. Geofence real, reboot e homologação por aparelho seguem os roteiros manuais de
`docs/validation/GEOFENCE_SCENARIOS.md` e `RELEASE_CHECKLIST.md`.
