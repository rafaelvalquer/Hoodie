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

### Diário Visual

A aba **Diário** reúne Hoje, Ontem e datas escolhidas: resumo dos contextos,
timeline do usuário e do Hoodie, cidade em pixel art, detalhes das visitas e replay
com pausa, reinício e velocidades de 1, 5 ou 10 minutos do dia por segundo.
O mapa reconstrói lugares conhecidos e transições simbólicas; funciona offline e
não grava uma trilha GPS. Visitas repetidas ao mesmo prédio continuam separadas
nos detalhes e no replay. O Developer Lab oferece um dia sintético para revisão.

O plano e a evidência de implementação estão em
[plano do Diário](docs/diary-development-plan.md) e
[andamento do Diário](docs/diary-development-progress.md).

### Fluxo principal

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
| Persistência | `core.database`, `core.datastore`, `data.repository` | Room v7 (21 tabelas, migrações versionadas) + DataStore; timeline ligada à origem (`TimelineRepository`) |
| Segurança | `core.security` | Banco inteiro cifrado com SQLCipher (senha aleatória embrulhada por chave do Android Keystore) + coordenadas cifradas (AES‑256‑GCM) |
| Sensores | `core.location`, `core.geofence`, `receiver` | Permissão em etapas (`LocationPermissionState`), até 95 geofences priorizados, erros visíveis, reboot/fuso/hora |
| Regras puras | `engine.context.ContextScorer`, `ConfirmationPolicy`, `engine.routine`, `engine.hoodie.HoodieDecisionEngine`, `NeedsEngine`, `HoodieSimulator` | Sem Android: 100% testáveis |
| Orquestração | `engine.context.ContextEngine`, `ContextTransitionService`, `engine.hoodie.HoodieEngine`, `engine.memory`, `engine.dialogue` | Aplica regras ao banco (só por boundaries), perguntas, notificações |
| Background | `worker` | Reconciliação a cada 15 min, checagem de almoço (+15 min) e de deslocamento longo (+40 min) |
| Pixel engine | `pixel.*` | Sprite procedural ou sprite sheet, 142 animações, 19 cenas, iluminação, partículas, transições |
| UI | `presentation.*` | MVVM com Hilt, Navigation Compose, Material 3 |
| Diário Digital | `core.deviceusage`, `engine.deviceusage`, `domain.phoneinsights`, `pixel.phoneinsights`, `presentation.screens.phoneinsights` | Uso do celular (UsageStatsManager) → sessões → agregados por dia, cruzados com os contextos |
| Mobilidade | `core.mobility`, `engine.mobility`, `receiver.ActivityTransitionReceiver` | Activity Recognition (transições, sem GPS contínuo) → `MobilityEngine` (estado, score, no máx. ~1 pergunta por trajeto, aprendizado, chegada) → `ContextEngine` e perfil visual (cena específica para carro, ônibus, trem, metrô, bicicleta, caminhada ou fallback genérico) |

### O princípio mais importante

Nada roda "contando o tempo". O estado do gato é `startedAt` + `expectedEndAt` + necessidades em `needsAt`.
Ao abrir o app (ou quando o worker roda), `HoodieSimulator.advance()` **reconstrói** tudo o que aconteceu no
intervalo, usando seeds derivadas do próprio instante. Abrir às 10:30 ou ter o worker rodando a cada 15 minutos
produz **exatamente o mesmo estado** (teste `CT-PERSIST`).

### Context Engine (sem IA)

`ContextEngine` é a fachada que serializa as operações com um único mutex.
Handlers internos cuidam de geofence, perguntas, fallback de rotina, contexto manual
e aprendizado de lugares. `ContextSignalProcessor` compartilha scoring e reações;
`ContextTransitionService` mantém a escrita das transições em um só lugar.

Confiança determinística: lugar conhecido +40, horário esperado +30, dia esperado +20, histórico +10..30.
`≥ 85` aplica · `40–84` aplica e pergunta · `< 40` assume `UNKNOWN`.
Perguntas: no máximo 4 por dia e nunca o mesmo contexto em menos de 60 min. Três almoços confirmados
num horário parecido → classificação automática, sem perguntar.
GPS oscilando na borda (saída + volta em < 5 min) desfaz a saída. Sem localização: **rotina provável**.

### Motor visual

O catálogo procedural está dividido em nove famílias em `pixel.animation.definitions`,
reunidas por `AnimationRegistry`. O contrato `HoodieClips` continua disponível.
`AnimationRegistryTest` compara os 84 clips com uma referência capturada antes da
divisão, incluindo poses, duração de cada frame, eventos e políticas de interrupção.

`TransitionPlanner` calcula scripts; `LoopSelector` escolhe microações e durações;
`InterruptResolver` respeita os frames do provider; `ReactionResolver` agenda reações;
`AnimationPlayer` mantém o relógio do clip. A máquina coordena a cena e executa os passos.
O fallback procedural delega a `pixel.sprite.procedural` as partes do corpo e acessórios.
`ProceduralPainterRegressionTest` preserva pixels, âncoras e camadas semânticas de 1.017
frames nas três vistas, comparados ao pintor anterior à divisão.

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
* **Clips** (`AnimationClip`): 142 animações com duração por frame, `InterruptPolicy`
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
* **Cenas por tipo de local**: Escola (estudar, escrever, virar a página — o livro abre e a página vira na mesa),
  Compras (prateleira → produto na mão → carrinho → caixa, com buraco na gôndola e esteira ativa),
  Família (conversa com alguém fora da cena, escutar, rir, petisco que some do prato) e Passeio
  (parque, praça ou área verde conforme a variante do dia; banco, mirante e foto com enquadramento).
  As escolhas usam as necessidades: foco baixo distrai o estudo, fome puxa petiscos, social baixo vira conversa,
  cansaço leva ao banco. `GENERIC_INDOOR/OUTDOOR` ficam só como fallback (viagem).
* **Transporte**: perfis únicos para caminhada, carro, ônibus, trem, metrô, bicicleta, outro veículo e transporte público não identificado; cada perfil liga cena, entrada/saída, animações, balanço/paralaxe e visual da rota/Jornada. Modais identificados têm cenas próprias e alternativas desconhecidas usam apresentação neutra.
* Iluminação por overlay (manhã, dia, entardecer, noite) com áreas emissivas e halos de lâmpada em degraus.
* **Personagens ambientais (NPC Art System V3)**: Bulldog executivo, cachorros, coelhos, rato, pato, guaxinim
  e gatos no mesmo canvas 48×72 do Hoodie, com até 10 cores cada. `CharacterArtProfile` (silhueta, rosto,
  proporções, sombreado, nível de detalhe) define cada espécie; o corpo é montado como silhueta
  (pescoço → ombros → tronco → quadril → pernas → pés) com luz no alto à esquerda e sombra embaixo à direita.
  Roupas em arquivos próprios (terno, casual, estudante, esporte, comutante, moletom) com frente, perfil e
  costas, mais mochila ou bolsa transversal. Animações calculadas: idle com respiração, passada de 8 fases
  sincronizada com o deslocamento (pé plantado não desliza), olhar, fala com gesto, sentar em transição,
  celular, refeição, sono, virar, entrar/sair e reações raras. Comportamentos determinísticos por cena,
  seed e tempo (ex.: Bulldog entra, olha o Hoodie, fala, espera e sai). Escala por profundidade
  (0,75–1,0) e sombra no chão. O Hoodie continua no renderer legado, idêntico pixel a pixel.
* **Pixel Lab**: ferramenta de animação com animação × direção × postura × expressão × velocidade,
  avanço frame a frame, onion-skin, âncoras, bounding box e linha dos pés; laboratório de cena com transições,
  filtro por grupo, variante do cenário e modo atividade × contexto × energia × humor; aba NPC com
  inspector (play/pause, quadro a quadro, 0,25×–2×), onion skin, escala, ambiente e Hoodie lado a lado;
  galerias de animações e cenas; e a fonte de cada sprite (sheet ou procedural).

## Mapa do Dia 2.0 — Jornada Pixel

O mapa do Diário mostra **a ordem do dia, não a geografia**: cada visita é uma parada própria (lugares repetidos
aparecem repetidos, com "Retorno #2"), em zigue-zague, e cada deslocamento é um trecho de rua que o Hoodie percorre
de verdade no replay — a pé, de bicicleta, de carro, de ônibus ou de trem/metrô.

```
DailyDiary (+ movements) ─► JourneyMapAssembler ─► JourneyMapData (nós + trechos com meio dominante)
                         ─► JourneyLayoutEngine + JourneyPathBuilder ─► JourneyLayout (240 × altura do dia)
ReplayUiState ─► JourneyReplayAssembler ─► JourneyScene ─► JourneyMapRenderer (7 camadas) ─► JourneyMapView
```

* **Camadas**: céu do horário · chão/ruas em cache · vida (árvores balançando, sombras de nuvem, água, fumaça, carros,
  passarinhos, folhas) · traço por meio (🚶 pontilhado verde, 🚗 dupla cinza, 🚌 azul, 🚇 trilho roxo/ciano, 🚲 amarelo)
  · paradas (visitada / atual pulsando / ainda não / selecionada) · luz contínua por minuto + postes, janelas e vaga-lumes · Hoodie.
* **Replay 2.0**: ⏮ / ⏭ entre chegadas e saídas, barra temporal arrastável, velocidade; o rastro já percorrido fica dourado.
* **Detalhe da parada**: chegada/saída, tempo, vezes no dia, total no lugar, atividade do Hoodie, como chegou e celular na visita.
* **Compatibilidade**: substituído pelo Mapa do Dia 3.0 quando `HoodieConfig.DIARY_JOURNEY_MAP_V3` está ligado.
* **Desempenho**: layout e camada estática uma vez por dia; ~2,5 FPS parado, ~10 FPS no replay, pausado fora da tela.
* **Golden**: `JourneyGoldenTest` exporta 8 estados para `app/build/pixel-preview/journey/` e compara com
  `journey-map-v1.sha256` (regravar com `JOURNEY_GOLDEN_RECORD=1` depois de revisar os PNGs).

## Mapa do Dia 3.0 — Overworld em capítulos + Relógio do dia

O Diário tem duas vistas do mesmo dia, escolhidas no seletor **[JORNADA] [RELÓGIO]** (salvo no DataStore em
`diary_map_mode`). Plano completo em [docs/journey-map-v3-plan.md](docs/journey-map-v3-plan.md).

```
JourneyMapData ─► JourneyChapterPlanner (+ QuickStopClusterer) ─► JourneyPlan (Single | Chapters)
               ─► SerpentineLayoutEngine + JourneyPathBuilder ─► OverworldLayout (240 × até 3 linhas por capítulo)
ReplayUiState  ─► JourneyReplayAssembler.overworld ─► OverworldJourneyRenderer ─► JourneyOverworldMapView / JourneyChaptersView
JourneyMapData ─► DayClockAssembler ─► DayClockData ─► DayClockRenderer ─► DayClockView
```

* **Jornada**: até `JOURNEY_SINGLE_MAP_MAX_STOPS` (9) paradas é um mapa só; acima disso vira **Manhã / Tarde / Noite**
  (12:00 e 18:00, no fuso do dia — dias de 23/25 h incluídos). A visita pertence ao capítulo da chegada; se continua no
  seguinte, aparece lá como **fantasma**. Trechos que atravessam capítulos saem pela borda de baixo (portal) e entram
  pela de cima. Acima de `JOURNEY_CHAPTER_MAX_STOPS` (12) paradas num capítulo, paradas rápidas seguidas (< 10 min)
  viram um marco "×k" (a atual/selecionada nunca é agrupada). Só um capítulo fica aberto: o do replay, o escolhido,
  o de agora (hoje) ou o de maior permanência.
* **Overworld**: serpentina de 3 colunas com curvas em U nas bordas; cada tipo de lugar é um bioma 32×32 (casa,
  castelo-escritório, templo, taverna, café, torre do mago, mercado, parque, chalé da família, acampamento, marco),
  com estados futuro (dessaturado), visitado, atual (placa dourada) e fantasma; trilhas com cor **e** padrão por meio.
* **Relógio**: 24 h num anel (meia-noite no topo, sentido horário); permanências são arcos grossos, trechos arcos
  finos, paradas curtas viram tiques (agrupados quando colados). No replay a parte futura fica dessaturada e o
  ponteiro leva o Hoodie. Desligável com `DIARY_CLOCK_VIEW`.
* **Mesmo replay nas duas vistas**: trocar de vista mantém hora, velocidade e parada selecionada.
* **Acessibilidade**: cada construção, capítulo, arco e tique tem alvo ≥ 48 dp e descrição completa para o TalkBack.
* **Labs**: Diary Lab → "Jornada 3.0" (dias sintéticos de 0 a 20 paradas, slider de hora, forçar mapa único/capítulos,
  Jornada e Relógio lado a lado); Pixel Lab → aba "Biomas".
* **Golden**: `JourneyV3GoldenTest` e `DayClockGoldenTest` exportam para `app/build/pixel-preview/journey-v3/` e
  comparam com `journey-map-v3.sha256` / `day-clock-v3.sha256` (regravar com `JOURNEY_GOLDEN_RECORD=1`).

## Relógio do Dia 2.0

A aba **[RELÓGIO]** do Diário mostra o dia num mostrador pixel art de 24 horas: onde a pessoa esteve, como se
deslocou e o que o Hoodie fez. Progresso e decisões em [docs/day-clock-progress.md](docs/day-clock-progress.md).

```
DailyDiary (+ movements, contexts, activities) ─► DayClockAssembler ─► DayClockData (Stay / Move / Unknown, minutos do dia)
DayClockData + "agora" (ao vivo ou replay) + seleção ─► DayClockRenderer (312×312) ─► DayClockPanel (escala inteira, sem filtro)
```

* **Regras**: recorte na meia-noite local e no "agora"; visitas repetidas separadas (`journey-i`, os mesmos ids da
  Jornada); deslocamentos com o meio dominante da Jornada; buracos sem dado viram *Unknown*, nunca suposição; dias de
  23/25 h pelo fuso. O anel usa as categorias dos cards "Seu dia" (Casa, Trabalho, Transporte, Almoço, Academia,
  Lazer, Outros), pelo contexto dominante de cada visita.
* **Camadas**: estática (placa com anéis, anel de atividades com borda interna escura, trilha pontilhada a pé /
  tracejada de veículo na cor da Jornada, futuro em xadrez, separadores, seleção dourada, marcações de hora, céu por
  período com dithering e estrelas, linha do agora, plaquinhas 00/06/12/18 com dígitos 5×7, ícones 15×15) e dinâmica
  (o mesmo Hoodie da Jornada — a pé, bicicleta, carro, ônibus, trem, metrô — com respiração e brilho pulsante).
* **Resolução**: pixel art fina — 312×312 px lógicos (base 104 × `DayClockGeometry.K` = 3), ×3 num Pixel 8 (~1 dp
  por pixel). Telas com menos de 312 px de largura reduzem o mostrador com filtro.
* **UI**: toque no anel seleciona o trecho (centro volta ao agora; futuro é ignorado), centro em Press Start 2P,
  e barra "Tempo por lugar"; a lista das paradas é a linha do tempo do Diário, logo abaixo (também o caminho do
  TalkBack). No replay o "agora" é o tempo do replay.
* **Desempenho**: raio e minuto de cada pixel em tabelas pré-calculadas; camada estática refeita fora da main thread
  só quando muda o minuto, a seleção ou o dia; ~8 FPS só com a tela visível.
* **Paleta**: fechada e sem cores novas (17 cores do Hoodie + biomas/trilhas da Jornada + céu por período) — testada.
* **Flag**: `HoodieConfig.DIARY_DAY_CLOCK_V2`; o relógio legado continua no Diary Lab, que também tem o
  "RELÓGIO DO DIA 2.0 · LAB" (dias sintéticos e controle do "agora").
* **Golden**: `DayClockGoldenTest` exporta 8 estados para `app/build/pixel-preview/day-clock/` e compara com
  `day-clock-v1.sha256` (regravar com `DAY_CLOCK_GOLDEN_RECORD=1` depois de revisar os PNGs).

## Diário Digital (Phone Insights)

O Diário passa a contar três camadas do mesmo dia: **vida real** (contextos e lugares) + **vida digital** (celular) + **vida do Hoodie**.

```
ANDROID USAGE STATS ─► UsageStatsSource (só foreground/background, tela, bloqueio)
        ▼
AppSessionBuilder ─► ScreenSessionBuilder ─► AppCategoryResolver ─► ContextUsageCorrelator ─► DailyPhoneUsageCalculator
        ▼                                                                 (PhoneInsightsAssembler, puro)
DeviceUsageRepository ─► Room v7 (agregados, horas, timeline e sessões) ─► DiaryDigitalMerger ─► Diário (aba Geral + aba Digital)
```

* **Permissão**: `PACKAGE_USAGE_STATS` é ligada pelo usuário em *Acesso ao uso*. Antes, a tela "Análise do celular" explica
  o que é visto (tempo de tela, apps, tempo por app, sessões, desbloqueios) e o que nunca é (mensagens, texto, fotos, conteúdo da tela).
  A análise começa desligada e só é ativada após consentimento e permissão. A ajuda de Configurações restritas abre as informações do app.
* **Cálculo**: sessões de tela por `SCREEN_INTERACTIVE → NON_INTERACTIVE`; desbloqueios por `KEYGUARD_HIDDEN`.
  Aparelhos sem esses eventos (API 26–27, alguns fabricantes) caem em estimativa a partir do uso de apps e a UI marca "≈".
  Launchers e a interface do sistema contam como tela ligada, não como app usado.
* **Categorias**: escolha do usuário → mapa interno (YouTube → Vídeo, Spotify → Música, Teams → Trabalho...) → `ApplicationInfo.category` → Outros.
* **Persistência**: Room v7 mantém `daily_device_usage`, todos os apps em `daily_app_usage`, rankings por contexto,
  totais em `daily_context_usage`, 24 horas em `daily_screen_hourly`, blocos em `daily_phone_timeline` e `phone_app_sessions`.
  Migrações 1→2→3→4→5 preservam os registros anteriores. Categorias manuais ficam em `app_category_overrides`.
  Eventos brutos nunca são salvos. O Android só guarda eventos por alguns dias: um recálculo "menor" de um dia antigo não sobrescreve o histórico.
  Uma sessão que cruza contextos é recortada nos limites, sem atribuição pelo ponto médio. Sessões são retidas por 365 dias;
  os agregados diários permanecem. Dias v4 não possuem horas/timeline que nunca foram gravadas.
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
* Mobilidade (opcional, Ajustes): guarda só **meio + horários + lugar de origem/destino** de cada trecho. Nunca rota, ruas ou coordenadas; no máximo uma leitura pontual de posição ao parar, para resolver a chegada. "Apagar histórico de deslocamentos" limpa só essa camada.
* Diário digital (opcional): **app + horários + duração**, contexto e agregados por dia. Nunca mensagens, texto digitado, fotos ou conteúdo da tela.
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
3. Integrar a fonte pixel licenciada sob OFL e validar sua leitura nas telas pequenas.

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


## Estabilidade e validação da versão 0.2

O plano técnico é acompanhado em [stability-architecture-progress.md](docs/stability-architecture-progress.md). A versão permanece dev enquanto os gates de teste, UI e revisão artística estão em andamento.

O banco atual é Room v7: v5 completa o histórico digital, v6 adiciona mobilidade e v7 acrescenta índices compostos sem reescrever dados. Sessões individuais são mantidas por 365 dias; agregados diários permanecem. Ver [auditoria dos índices](docs/database-index-audit.md).

O Diário Digital começa desativado. Ativar solicita o acesso ao uso do Android; a análise só liga após a permissão. A ajuda de Configurações restritas está disponível na tela de ativação. A permissão Android sozinha não equivale a consentimento no app.

A UI usa erros tipados, eventos coletados conforme o ciclo de vida e textos em recursos. Falha de edição nunca vira cadastro novo; falhas ao carregar um lugar permitem tentar novamente ou voltar. Falha de geofence após salvar aparece em Snackbar, com nova tentativa que não grava o lugar novamente.

As telas usam Press Start 2P (SIL OFL 1.1) em títulos, labels, botões e números; descrições longas usam fonte normal. Ver [fonte e licença](docs/pixel-font.md). A [regressão visual](docs/visual-regression.md) cobre seis telas, cinco estados, três dimensões e duas escalas de fonte, além do fim da rolagem e mapa do Diário.
