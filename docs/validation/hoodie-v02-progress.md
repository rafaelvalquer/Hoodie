# Hoodie V0.2 — acompanhamento da sprint

Especificação integral: [plano da sprint](../plans/hoodie-v02-sprint.md).
Base encontrada: `420ed190` (sons das interações), sobre `7fe1927c` (base citada no plano).
Objetivo completo: implementar H-04 a H-08, na ordem das cinco fases do plano, e homologar o comportamento integrado. **A sprint ainda não está concluída.**

## Fase 1 — H-06 e H-05

Implementação validada na Fase 1 (homologação integrada final ainda pendente):

- Identidade derivada `PresentedHoodieContext`: evento, contexto canônico, lugar, tipo de estabelecimento, atividade, cena, revisão e reconciliação.
- A identidade entra em `VisualState` e é confirmada pelo callback posterior à apresentação do bitmap. Interações ficam inelegíveis enquanto snapshot e evento divergem ou o frame pertence a outra revisão. Sem temporizador arbitrário para reconciliar, sem consulta ao GPS ou gravação no Room para confirmar apresentação.
- `HoodieReactionCoordinator`, contrato único de intenção, fonte/prioridade, apresentação e ciclo de vida com motivos. Uma reação despachada e uma pendente preferencial; expiração e cancelamento por contexto/ciclo de vida.
- Ordem: segurança, ação essencial, usuário, social, personalidade, ambiente. Prioridade não interrompe clips essenciais; o motor conserva seus limites seguros e objetos.
- Adaptadores de `HomeUiEvent.React` e `InteractionResponse`; o caminho anterior continua disponível com a flag desligada. Há entrada explícita para eventos sociais que controlam o Hoodie. Os microeventos ambientais atuais declaram não envolver o Hoodie e mantêm seus motores e balões próprios.
- Personalidade prepara decisões e confirma ledger/cooldown apenas após `RenderFrame` apresentado. Fala sem gesto também aguarda o limite seguro e recebe confirmação visual, sem pausar a microação. Ambiente usa reserva e confirmação de apresentação.
- Um modelo `ReactionPresentation` para o único balão principal. Saída da Home, contexto e cena cancelam a propriedade de apresentação anterior. Som continua restrito às interações do usuário.
- Novas flags declaradas com valor inicial `false`; ainda sem ativação destinada aos usuários.

Testes escritos: `HoodieReactionCoordinatorTest`, `ReactionPriorityTest`, `ReactionLifecycleTest`, `PersonalityPresentationTest`, `ReactionDialogueIntegrationTest`, `PresentedHoodieContextTest`, `HomeContextReconciliationTest`, `HoodieInteractionContextConsistencyTest`, `InteractionPlaceChangeTest`. O teste real ViewModel/Room existente foi ampliado para flags ligadas/desligadas.

Evidências finais com fontes congeladas:

- `build/v02-phase1-stable-build-tests.log`: build debug e APK de testes aprovados; 71 testes JVM sem falhas.
- `build/v02-phase1-stable-lint.log`: lint aprovado, sem novas supressões.
- `build/v02-phase1-device.log`: 29 testes Android aprovados, incluindo conflito no Canvas e balão único.
- Integrações verificam correção manual, confirmação visual, cancelamento e zero invalidações Room.
- Rodadas iniciais em `v02-phase1-gates.log` e `v02-phase1-final-build-tests.log` tiveram falhas e foram substituídas pela execução estável; não são evidência de aprovação.
- [ ] Continuidade de sessão/posição dos NPCs será medida junto com H-07.

## Fase 2 — H-04

Implementação em validação: 35 regras novas (catálogo total 145), com as frases e sequências exatas do plano. Condições usam animação e flags dos objetos do frame apresentado. Cinema utiliza `CinemaAmbient.phase` da mesma renderização, sem um relógio paralelo. A seleção das novas regras tem flag independente e conserva fallback global quando desligada. Sono, caminhada, sorvete e entrada em loja bloqueiam carinho; os demais clips conservam suas políticas de limite seguro.

`HoodieVenueInteractionsTest` cobre elegibilidade das 35 regras, objetos ausentes, fases do cinema, fallback desligado e bloqueios. A rodada inicial (`build/v02-phase2-tests.log`) encontrou e permitiu restringir a condição de término do filme. A rodada corrigida `build/v02-phase2-stable-tests.log` passou: 46 testes JVM e build debug. `HoodieVenueContinuityTest` verifica limites seguros, cursor, posição e flags durante reações em pastel/café/pipoca/produto/check-in/elevador no motor real. A apresentação Android integrada permanece na homologação final.

- [x] Catálogo com todas as frases do plano para BAKERY/MALL/BEACH/CINEMA/PHARMACY/HOTEL, no catálogo já existente.
- [x] Condições baseadas em animações reais; nunca mencionar objeto ausente.
- [x] Segurança de sorvete, sono, caminhada, entrada em loja, bebida, pastel, pipoca, produto, check-in/elevador.
- [x] Feature flag de interações de estabelecimentos independente das flags de seleção automática de cena.
- [x] Testes de catálogo, condições, fallback e continuidade dos objetos (JVM); Android final pendente.

## Fase 3 — H-07

Iniciada a linha de base anterior às otimizações: `ScenePerformanceBenchmarkTest` usa as 11 cenas reais e nove estágios por cena (caches frios, aquecimento, contínuo, histórias em 90 s e 240 s, noite, saída, retorno e toque com máscara). Mede custos P50/P95/P99 do renderer, alocações da thread JVM, heap e GC; registra checksums dos pixels e impressão digital dos fontes. A medição inicial identificou ausência de reação efetiva no Mercado; a carga foi corrigida para exigir início real em cada cena. Rodada aceita: `build/v02-phase3-baseline-verified.log`, 11 cenas e 99 estágios, sem falhas. Referência preservada em [benchmarks/v02](benchmarks/v02/README.md), com CSV, ambiente e log. A primeira tentativa teve erro de classpath JVM e não conta como medição; os resultados brutos anteriores ao ajuste de toque não serão usados para comparação final.

Instrumentação adicionada, ainda em homologação: `SceneDiagnostics` mantém janelas limitadas e independentes por cena para desenho, planejamento, estado, objetos, composição, luz, pós-processamento, bitmap e intervalos de apresentação. FPS efetivo e mediano só existem após publicações reais; estatísticas ART indisponíveis ficam nulas. Eventos de sessão/timeline/snapshot têm contadores específicos. `ScenePerformanceTab` integra o Developer Lab com coleta por lease local, cenas fictícias, execução reproduzível e exportação CSV explícita. `ScenePerformanceDeviceTest` usa o Canvas de produção nas 11 cenas, com relógio de animação fixo e relógio monotônico real para medir cadência.

Gate inicial do diagnóstico: `build/v02-phase3-diagnostics-build.log`, aprovado (2 testes JVM, debug e APK de testes). Após revisão, foram ajustados a espera pela cena selecionada, o relógio fixo de benchmark e a distinção FPS efetivo/mediano. A rodada `v02-phase3-diagnostics-stable-build.log` falhou ao mover um cache de transformação do Gradle no Windows. A execução serial `build/v02-phase3-diagnostics-final-build.log` passou: 3 testes JVM, APK debug e APK instrumentado. O painel cancela a execução e libera a coleta ao receber `ON_STOP`.

Referência Android finalizada: `build/v02-phase3-device-baseline.log`, **OK (1 test)** em 403,516 s; 11 cenas e 99 amostras, incluindo confirmação real de toque em cada uma. Dados e limitações preservados em [ANDROID-BASELINE.md](benchmarks/v02/ANDROID-BASELINE.md). Debug/teste congelados em `build/validation/android-baseline-apks/`, hashes no relatório. A rodada foi executada sem build Gradle ou benchmark JVM concorrente.

Depois de congelar o APK da referência, foram preparados `SceneCompositionPlan` (pool de entradas, ordenação estável de props/NPCs/objetos sem listas/closures por quadro) e extensão do caminho `Dispatchers.Default` para as 11 cenas complexas, mantendo o mutex através da apresentação e sem fila de frames. A composição foi validada em `build/v02-phase3-composition-worker-tests.log`: 7 testes JVM, ambos APKs e **99/99 checksums de pixels idênticos** à referência. Comparação de alocações preservada em [COMPOSITION-COMPARISON.md](benchmarks/v02/COMPOSITION-COMPARISON.md).

Em seguida, a chave de reuso foi corrigida para conservar o elenco e o roteiro em andamento ao mudar apenas a iluminação. A política noturna continua determinando o elenco inicial de uma nova visita; lugar/dia/variante continuam invalidando a sessão. `NpcSessionReuseTest` verifica identidade da sessão, identidade do snapshot, preservação de contadores e uma nova sessão para mudança legítima. Gate `build/v02-phase3-session-reuse-tests.log` aprovado: 7 testes JVM e ambos APKs. Testes incluídos: diagnóstico (3), entradas/snapshots reutilizados, cache temporal dos seis estabelecimentos, renderers concorrentes e continuidade por iluminação.

Comparação Android completa aprovada em `build/v02-phase3-device-optimized.log`: 11 cenas / 99 amostras em 405,247 s. Dados em [ANDROID-COMPARISON.md](benchmarks/v02/ANDROID-COMPARISON.md). O Restaurante apresentou piora no início da rodada, portanto a aceitação de desempenho continua pendente nesse ponto. O resultado precisa ser auditado antes de homologar H-07. Permanecem os gates finais de memória prolongada, painel/ciclo de vida/exportação, regressão visual e lint. A continuidade noturna preserva mais atores do que a referência que os removia abruptamente; comparar essa carga explicitamente, sem chamar a diferença de custo de regressão equivalente.

- [x] Instrumentação por cena e publicação separada de bitmap, restrita ao diagnóstico.
- [x] Referência JVM e Android das 11 cenas, preservada com limitações e hashes.
- [x] Composição reutilizada e prova de pixels antes da alteração intencional da continuidade noturna.
- [x] Snapshots compartilhados por timestamp e sessões preservadas em mudanças de iluminação/revisão.
- [x] Worker com mutex e sem fila, compilado; medição Android comparativa pendente.
- [x] Developer Lab com benchmark e exportação explícita; homologação visual/ciclo de vida pendente.
- [ ] Comparação Android final, memória prolongada e gates integrados de H-07.

Gates adicionais:

- `build/v02-phase3-memory-visual-lab-build.log`: 58 testes JVM aprovados (inclui as suítes dos estabelecimentos, goldens/matrizes e ciclos de memória), debug e teste APK aprovados.
- `build/v02-phase3-lab-device.log`: **OK (1 test)**; painel real exportou CSV pelo callback de produção e cancelou ao ir para segundo plano. Destino do seletor de arquivo foi substituído pelo registry de teste, sem enviar dados. Captura inicial do teste usava fundo branco externo ao Scaffold; wrapper foi corrigido para a superfície escura real e será capturado novamente.
- Leitura visual das folhas de Padaria, Cinema, Praia, Shopping, Farmácia e Hotel mostrou props/profundidade coerentes. A nova captura do painel usa a superfície escura real; texto, botões, prévia e métricas estão legíveis.
- Cache do estado do cliente do Restaurante e slots da história passaram em `build/v02-phase3-restaurant-cache-build.log`: 54 testes JVM e ambos APKs, incluindo reconstrução fresca vs cache através de seek e 9/9 checksums da cena preservados.
- A análise de 18 ciclos identificou poses crescendo de 295 para 557. LRU de 256 poses aprovado em `build/v02-phase3-bounded-memory-gate.log`: 61 testes JVM, incluindo 18 ciclos, e ambos APKs. Poses permanecem em 256, composição em 15 entradas; após aquecimento, heap oscila aproximadamente entre 62,4 e 63,3 MB nos ciclos finais. O teste de descarte confirma recriação com pixels/âncoras idênticos.
- Medições isoladas aquecidas completas: [RESTAURANT-WARM-COMPARISON.md](benchmarks/v02/RESTAURANT-WARM-COMPARISON.md). Worker foi desfavorável para essa cena; a versão atual usa Main com cache e cadência estável. Dez outras cenas complexas usam worker. A meta de intervalo P95 de 40 ms segue pendente na referência emulada.
- `build/v02-phase3-frame-pacing-gate.log`: 7 testes JVM e ambos APKs aprovados para prazos estáveis, LRU, cache do Restaurante e memória.
- `build/v02-phase3-ui-pacing-regression.log`: **OK (30 tests)** em 157,037 s; painel/exportação/ciclo de vida, gestos, balão único, continuidade de objetos e fontes ampliadas.
- Não marcar a sprint nem as metas finais de desempenho como concluídas: ainda faltam a matriz final das fontes integradas, lint e os gates da Fase 5.


## Fase 4 — H-08

Implementados em memória: 11 atividades fictícias, janela explícita 07:00–20:00, datas civis corretas em transições DST, mesmas identidades de Casa/Trabalho nos retornos. O caminho estático anterior pede `includeReplayActivities=false`; a flag de replay permanece falsa.

`DemoReplayTimeline` fornece sete etapas, narrativas fictícias, destaques, tour de 50 s e interpolação contínua das viagens. `DemoReplayController` é puro e recebe tempo monotônico: play/pausa/retomar/reiniciar/seek/eventos/modos/velocidades/fim e restauração sem relógio em background. `DemoDiaryAssembler` reutiliza os montadores reais com as atividades e a janela. `DemoReplayPresentation` projeta mapa, HUD, atividade, narrativa e prévia do mesmo instante fictício. `DemoReplayViewModel` salva somente valores primitivos no `SavedStateHandle`, pausa ao ocultar e retoma apenas uma sessão que estava tocando.

Gates: `build/v02-phase4-fictitious-timeline-tests.log` (7 testes), `build/v02-phase4-replay-controller-tests.log` (16), `build/v02-phase4-navigation-tests.log` (19) e `build/v02-phase4-presentation-tests.log` (21), aprovados. O `DemoReplayViewModelTest` tem 4 casos aprovados para pausa/retorno, tempo oculto, restauração de valores primitivos e fim/saída. `DemoReplayIsolationTest` (1 caso) contou todas as tabelas Room antes/depois e confirmou que navegação, prévia e saída não escrevem nem notificam. `build/v02-phase4-ui-tests-build2.log` compilou o APK instrumentado. `build/v02-phase4-ui-device-final3.log` aprovou a UI no Pixel 8 virtual: banner, seleção de modo, prévia, navegação e saída. O teste inicial revelou que executar um tour de 50 segundos durante a asserção podia concluir antes do teste; a cobertura instrumentada agora evita playback contínuo e os testes puros cobrem duração e estados do playback.

O `DemoDayScreen` usa o caminho anterior enquanto `HOODIE_DEMO_REPLAY_V1` estiver desligada; a entrada nova reutiliza mapa, HUD e controles do Diário e oferece a cena contextual opcional. A Fase 4 está implementada e passou pelos gates unitários, de isolamento e UI descritos acima. A flag segue desligada até a homologação integrada da Fase 5.

- [x] Atividades fictícias somente em memória, entregues ao DiaryAssembler real, janela explícita 07:00–20:00.
- [x] `DemoReplayTimeline`, `DemoReplayController`, `DemoReplayPresentation`, `DemoReplayViewModel`.
- [x] Tour de aproximadamente 50 s, sete etapas, narrativa fictícia e deslocamento contínuo.
- [x] Jornada completa em 5/10 min/s, sem alterar o replay real; play/pausa/retomar/reiniciar/anterior/próximo/seek/velocidade/fim.
- [x] Relógio monotônico único, pausa/saída sem execução em background, restauração coerente após recriação do ViewModel.
- [x] Reusar mapa, controles, HUD e montadores; Casa e Trabalho conservam placeId nos retornos.
- [x] Prévia contextual opcional usando o relógio fictício, isolada da Home.
- [x] Banner permanente e layout rolável; não acessar dados reais.
- [x] `DemoReplayControllerTest`, `DemoReplayTimelineTest`, `DemoReplayDeterminismTest`, `DemoReplayIsolationTest`, `DemoReplayNavigationTest`, `DemoReplayUiTest`.

## Fase 5 — homologação integrada

- [x] Suíte unitária e de integração final: gate histórico `build/v02-phase5-unit-lint-debug.log` (1.266 testes) e gate após o seguimento de pacing (1.270 testes em 290 suítes XML, 0 falhas/erros, 6 ignorados).
- [x] Instrumentação Android dos fluxos integrados e ciclo de vida: `build/v02-phase5-device-matrix.log`, 32 testes no Pixel 8 virtual, 0 falhas. Inclui replay, gestos, matriz de fontes/tamanhos, continuidade, diálogo, exportação do Lab e renderização das 11 cenas.
- [x] Lint e build debug finais: `:app:lintDebug` e `:app:assembleDebug` aprovados; lint mantém o aviso pré-existente `OldTargetApi` para `targetSdk = 36`.
- [x] Matriz de renderização e benchmark das 11 cenas executados duas vezes. A execução final aprovada está em `build/v02-phase5-final-performance.log`; evidências preservadas em [benchmarks/v02/final-android](benchmarks/v02/final-android/README.md).
- [x] Tour, determinismo, navegação, ciclo de vida e isolamento Room/notificações exercitados pela suíte JVM, teste de isolamento e UI instrumentada.
- [x] Auditoria dos requisitos H-04 a H-08: implementados e cobertos; as flags de interações, coordenador, ambientes, apresentação, diagnóstico de desempenho e replay permanecem `false`.

Pendência de homologação: a medição aquecida original terminou aprovada, mas não atingiu a meta de desempenho; os dados originais estão na [captura CSV](benchmarks/v02/final-android/scene-performance-final-warmed.csv). Uma rodada direta posterior alcançou 29,51–30,08 FPS efetivos, mas na repetição de 16/10/2026 o intervalo P95 ficou entre 44,25 e 65,26 ms e o FPS efetivo entre 27,33 e 30,05; nenhuma das 11 cenas atingiu P95 ≤40 ms. Veja a [matriz mais recente](benchmarks/v02/final-android/scene-publication-cadence.csv) e o [relatório do seguimento](benchmarks/v02/final-android/PACER-FOLLOWUP.md). O dispositivo era o emulador `sdk_gphone16k_x86_64`, Android 17 / API 37, tela 1080×2400 a 60 Hz. H-07 permanece aberto e as flags de interação e replay continuam desligadas.

### Seguimento de cadência e diagnóstico

O pacer agora usa `1_000_000_000 / 30` ns (33,333 ms), com tolerância de arredondamento de 2 ns e descarte de prazos perdidos. O instrumento detalhado é ativado por lease local no Developer Lab e nos testes de desempenho. O caminho debug normal não mede Office/Store por exceção: tanto os tempos por NPC quanto a cópia do bitmap ficam condicionados ao lease. Isso também mantém os testes de Escritório e Loja cobertos com leases explícitos.

Após esses ajustes, `ScenePerformanceDeviceTest` foi repetido nas 11 cenas, com 15 quadros frios e 180 de aquecimento + 180 contínuos por cena, sem lease de diagnóstico. O teste passou em 3m47s. Na amostra contínua, FPS foi 27,33–30,05 (mediana 29,14); P50 foi 32,81–35,07 ms, P95 44,25–65,26 ms e P99 50,83–84,93 ms. 9/11 cenas ficaram em 28 FPS ou mais, e nenhuma cumpriu P95 ≤40 ms. A rodada direta anterior tinha P95 de 40,68–54,22 ms e FPS de 29,51–30,08, portanto a repetição não reproduziu esses valores. A [matriz CSV](benchmarks/v02/final-android/scene-publication-cadence.csv) preserva as três fases da rodada mais recente.

Auditoria do ponto de medição: `onScenePresented` era chamado logo após `Bitmap.setPixels`, antes do desenho do bitmap pelo Compose. Por isso, a série acima mede cadência de publicação. O teste agora também registra timestamps monotônicos num `Modifier.drawWithContent`, após o desenho do conteúdo pelo Canvas, sem alterar o app de produção. Nova matriz em 16/10: FPS 27,02–30,00 (mediana 29,50) e intervalo P95 43,97–64,23 ms (mediana 53,09); 10/11 cenas chegaram a 28 FPS, mas nenhuma atingiu P95 ≤40 ms. O teste passou em 3m03s. Consulte a [matriz de desenho Canvas](benchmarks/v02/final-android/scene-canvas-draw-cadence-20261016.csv) e o [ambiente](benchmarks/v02/final-android/scene-canvas-draw-cadence-20261016-environment.txt). Esse callback cobre o desenho Compose, não o instante de apresentação pelo compositor físico.

A matriz Canvas completa executou os nove estágios de H-07 nas 11 cenas, incluindo carinho real, em 7m03s. No estágio contínuo, FPS ficou em 28,03–30,05 (mediana 29,70) e intervalo P95 em 38,48–60,49 ms (mediana 49,45); apenas 2/11 cenas atenderam P95 ≤40 ms. Durante carinho, FPS ficou em 26,04–30,25 e P95 em 39,71–76,78 ms; 1/11 cenas atingiu a meta de P95. Evidências em [scene-canvas-draw-full-matrix-20261016.csv](benchmarks/v02/final-android/scene-canvas-draw-full-matrix-20261016.csv) e [ambiente](benchmarks/v02/final-android/scene-canvas-draw-full-matrix-20261016-environment.txt). A métrica mede desenhos do Compose, ainda sem confirmação de apresentação pelo compositor ou aparelho físico.

Perfis detalhados dos dois piores trechos contínuos encontraram render P95 de 16,08 ms em LEISURE e 14,92 ms em RESTAURANT, mas intervalos P95 de 53,53 e 53,18 ms, respectivamente. A cópia do bitmap ficou em 1,45 e 1,83 ms. Isso desloca a investigação restante para a cadência de publicação/agenda da UI e para a variabilidade do emulador, sem evidência para uma otimização específica do desenho dos NPCs. Os perfis estão em [scene-publication-cadence-profiles.csv](benchmarks/v02/final-android/scene-publication-cadence-profiles.csv). Os testes instrumentados direcionados de Office e Store também passaram após o gate explícito.

O A/B isolado de RESTAURANT comparou Main (29,52 FPS, P95 53,99 ms) com `Dispatchers.Default` (29,36/49,23 e 28,16/53,38 ms em duas amostras). A variação não demonstrou ganho líquido consistente; o despacho original foi mantido. A tentativa Release avançou até executar o runner depois que `StoreSceneDeviceTest` passou a ficar em `src/androidTestDebug`, pois depende de uma classe exclusiva do app Debug. A matriz ainda não iniciou: o teste falhou com `NoSuchFieldError` em `SceneBenchmarkScenes` após a minificação R8 transformar o singleton/campo `INSTANCE`. Nenhum quadro Release foi medido; selector de variant, assinatura, dependências e regras temporárias foram removidos. O registro A/B está em [scene-render-dispatch-ab-20261016.csv](benchmarks/v02/final-android/scene-render-dispatch-ab-20261016.csv), e a tentativa está detalhada em [PACER-FOLLOWUP.md](benchmarks/v02/final-android/PACER-FOLLOWUP.md).

Após a correção de sincronização do benchmark, o gate Android passou para as 11 cenas em 33 amostras de perfil e na matriz Canvas completa de 88 linhas. No perfil detalhado, render P95 ficou em 10,42–21,99 ms (mediana 14,06), mas intervalo P95 de publicação em 46,47–56,83 ms (mediana 53,32). Sem diagnóstico, a matriz completa em SwiftShader teve 0/11 cenas com P95 Canvas ≤40 ms no estágio contínuo; em carinho, 1/11 alcançou a meta e uma cena caiu a 26 FPS. O AVD foi então repetido com GPU host Intel: a matriz Core teve FPS de 29,20–30,19 e P95 Canvas de 47,81–61,53 ms (0/11 na meta). `FrameMetrics` mediu desenho UI P95 de 0,29–1,09 ms e GPU P95 de 29,11–34,76 ms. Um baseline `PixelImage` estático sem renderer/NPCs mediu P95 Canvas de 63,31 ms, então o AVD continua limitando esta meta mesmo com o dispatcher de cenas fora da UI. As novas evidências estão em [benchmarks/v02/final-android](benchmarks/v02/final-android/README.md). H-07 continua aberto: faltam a matriz GPU-host com toque/história, validação de apresentação pelo compositor e um aparelho físico; a tentativa instrumentada Release não mediu quadros por falha R8. As flags destinadas aos usuários seguem desligadas.



A validação final GPU host percorreu as 11 cenas em oito estágios, incluindo história e carinho, e passou em 7m30s. Canvas P95 no steady: 48,88–61,03 ms; story: 47,21–62,16 ms; touch: 46,99–61,74 ms. Nenhuma cena atingiu ≤40 ms em steady ou touch. Window FrameMetrics registrou UI draw abaixo de 1,22 ms e GPU P95 de 28,90–36,48 ms nos mesmos estágios. Com o baseline estático também acima da meta, não há evidência para uma otimização de NPC específica. H-07 continua aberto até validação em hardware físico e solução do limite de apresentação virtual. CSVs estão em [Canvas](benchmarks/v02/final-android/scene-canvas-host-gpu-full-matrix-20261016.csv), [FrameMetrics](benchmarks/v02/final-android/scene-window-frame-metrics-host-gpu-full-matrix-20261016.csv) e [ambiente](benchmarks/v02/final-android/scene-window-frame-metrics-host-gpu-full-matrix-20261016-environment.txt).
