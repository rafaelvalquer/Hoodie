# Hoodie Stability & Architecture — execução

Plano original: [stability-architecture-plan.md](stability-architecture-plan.md).
Trabalho diretamente em `main`. O objetivo só termina após implementar e verificar todas as fases.

## Estado atual da reintegração

Em 02/10/2026, a árvore reintegrada passou em 390 testes JVM (77 classes) e 72 testes Android API 34, sem falhas, erros ou ignorados; o teclado virtual real executou. Arte aprovada por Rafael, manifests sincronizados e fontes pixel OFL integradas. A matriz gravou 180 casos/222 imagens; todas foram inspecionadas e aceitas após recapturar Ajustes com os botões corrigidos. A comparação automática aprovou os 180 casos e 222 imagens, sem falhas ou ignorados, em 556,248 s. A auditoria final identificou ajustes adicionais nos alvos de toque das abas do Diário e recursos de texto dos cards digitais; sua implementação, recaptura e validação final, além da promoção dev → RC → estável, permanecem pendentes. A tabela abaixo é o registro histórico por fase; resultados posteriores detalhados ao final prevalecem sobre menções antigas a validações pendentes.
| Fase | Entrega | Estado e evidência |
| --- | --- | --- |
| 1 | Onboarding E2E real | Três cenários aprovados no Pixel 8 / Android 17, inclusive após a refatoração: UI desde Welcome, Casa cifrada, contexto HOME, reação do Hoodie e avanço; posição ausente recuperável; falha SQLCipher de escrita seguida de retry. Keystore/Room/SQLCipher/engines reais e DataStore isolado. Relógio Compose explícito evita aguardar o sprite contínuo. |
| 2 | Edição segura | Implementado; `editingId` ausente no banco falha explicitamente; repository não silencia update de local inexistente. Testes de regressão em validação. |
| 3 | PlaceLoadState | Implementado; Loading/Ready/NotFound/Error, retry e retorno. Validação de UI pendente. |
| 4 | Configurações restritas | Sheet e intent de informações do app implementados. Validação no device pendente. |
| 5 | Estado de permissão/UX | `UsagePermissionUiState` ligado à tela: DEBUG DENIED explica restrições, release oferece ajuda opcional. Verificação release pendente. |
| 6 | Opt-in digital | Default false; pedido persistido; ativação após permissão; Settings e Digital usam eventos para abrir a permissão depois da gravação. Verificação de consentimento/reinício pendente. |
| 7 | Room v5 | Implementado; Migration4To5 registrada, schema 5 exportado. Migrações JVM até v5 e SQLCipher Android aprovados. |
| 8 | daily_screen_hourly | Implementado; 24 linhas por dia, restauradas pelo mapper. |
| 9 | daily_phone_timeline | Implementado; blocos derivados persistidos com índices date/start. |
| 10 | daily_context_usage | Implementado; totais e sessões independentes do ranking. |
| 11 | replaceDay transacional completo | Implementado; replace/clear incluem as três tabelas novas. |
| 12 | Mappers históricos completos | Implementado; hourly/timeline/totais restaurados, categoria manual reaplicada. Dias v4 mantêm fallback explícito. |
| 13 | Persistência sem truncar apps | Implementado; removido TOP_APPS_STORED, ranking UI permanece limitado. |
| 14 | Split por interseção de contexto | Implementado; lacunas preservadas, contexto mais recente vence sobreposição, sem duplicar duração. |
| 15 | Testes históricos/migração/split/reopen | Aprovados Migration4To5Test, HistoricalPhoneInsightsTest, AppSessionContextSplitterTest; 40 apps, reopen, replace/rollback, migração e recortes. SQLCipher v5 no Android aprovado (40 apps, 24 horas, timeline e totais após reopen). |
| 16 | Geofence uma vez/dia | Implementado; epochDay persistido apenas após sucesso. Testes aprovados: hora 4, reinício, recuperação de falha e DataStore persistido. |
| 17 | Cancelamento PhoneInsightsWorker | Implementado via runWorkerTask; CancellationException propagada, falha logada e retry. |
| 18 | WorkerResult comum | Implementado e usado em Reconcile, Check e PhoneInsights; testes de sucesso, falha e cancelamento aprovados. |
| 19 | ContextEngine fachada/handlers | Implementado ContextSignalProcessor, GeofenceContextHandler, ContextQuestionHandler, RoutineFallbackHandler, ManualContextHandler e PlaceLearningHandler. ContextTransitionService permanece a única escrita de transições. 13 testes de integração e 9 de regras aprovados. |
| 20 | Mutex em um único nível | Implementado: mutex apenas na fachada; handlers não adquirem locks. Checagem de deslocamento também serializada, evitando sobrescrever contexto após leitura de posição concorrente. Teste aprovado de exclusão entre aprendizado e contexto manual, com registro externo suspenso e timeout para deadlock. |
| 21 | Definições de animação modulares | Implementadas nove famílias em definitions e AnimationRegistry; HoodieClips mantém o contrato público. Referência SHA-256 capturada dos 84 clips compilados antes da divisão: comparação aprovada de frames, poses, durações, eventos, loop, direção e política. 12 testes de comportamento aprovados. |
| 22 | State machine separada | Extraídos TransitionPlanner/AnimationSequence, LoopSelector, InterruptResolver, ReactionResolver e AnimationPlayer. Rodada final: 93 testes pixel aprovados, incluindo relógio/cursor do clip, preservação de ciclo ao mudar direção, reset ao reiniciar, fila de reações, provider e roteiros. Três E2E HOME aprovados após a refatoração. |
| 23 | Painter modular | Extraídos HoodiePoseRenderer, Body/Head/Arms/Legs/Tail/AccessoryPainter, primitivas e geometria compartilhadas. Fachada preserva paleta, IDs das partes, cache e APIs. Comparação aprovada da referência capturada antes da divisão para 1.017 frames dos 84 clips em frente/costas/lado, incluindo pixels, âncoras e mapas de partes. 92 testes pixel aprovados. |
| 24 | UiState + UiEvent | Aplicado nas cinco telas: canais buffered; coleta com lifecycle STARTED; Home reações/falhas, Onboarding configurações e estado de erro, Diary detalhes com data de origem, Settings mensagens/falhas/permissão, PhoneInsights permissão/falhas. Ações de Settings/PhoneInsights em nova validação. |
| 25 | Erros tipados | AppError e quatro famílias sem textos de UI. PlaceException carrega PlaceError; Home/Onboarding/Diary/Settings/PhoneInsights usam erros tipados e apresentação localizada. Cinco testes UiAction aprovados. PlacePicker ainda contém mensagens de erro textuais; auditoria de outras camadas pendente. |
| 26 | strings.xml | Migrados 154 textos estáticos das seis telas; mensagens de Settings e onboarding localizadas. Textos com interpolação, condições e helpers ainda pendentes. Nova compilação/Android em execução. |
| 27 | Acessibilidade | PixelButton/PixelPanel/ChipRow/MapOverlayButton: alvo mínimo 48 dp e papéis; PlaceTypeCell selecionável como RadioButton; fechar Pixel Lab com descrição e 48 dp. Criados três testes Android dos componentes. Auditoria completa e execução pendentes. |
| 28 | Screenshot/golden matrix | Pendente; testes existentes do PlacePicker ajustados para load Ready explícito. |
| 29 | Bottom navigation secundária | Implementado; somente rotas dos cinco tabs mostram a barra. Verificação UI pendente. |
| 30 | Resultados do mapa | Implementado; máximo 140 dp; scroll/zoom dismissResults. Verificação UI pendente. |
| 31 | Snackbar/retry geofence | Pendente |
| 32 | Retenção de sessões 365 dias | Implementado no ReconcileWorker; DAO exclui epochDay anterior ao limite, agregados preservados. Teste Room aprovado. |
| 33 | Índices/migração Room | Parcial: índice composto endedAt/start em context_events e índices date/start na timeline digital. Outros índices em auditoria. |
| 34 | Documentação | Plano preservado; docs funcionais pendentes. |
| 35 | Versionamento | Pendente; manter dev durante implementação. |
| 36 | Revisão de arte | Rafael revisou e aprovou walk, idle, sleep e work, conforme confirmação humana em 02/10/2026. Ambos os manifests registram manual-v1, manualReview=true e reviewedBy=Rafael; cópias conferidas idênticas. |
| 37 | Fonte pixel OFL | Pendente |

## Validação em andamento

Testes direcionados: `PlacePickerViewModelTest`, `PhoneDiaryIntegrationTest`.
Nova rodada: lógica digital, migrações, histórico/reopen/rollback, WorkerResult e geofence policy. A primeira tentativa passou 57 testes de lógica; seis classes Room não iniciaram porque Robolectric não podia gravar lock no perfil Windows. Segunda execução usa runtime local em modo offline, via init script temporário em app/build.
Resultado final JVM em 02/10/2026: **75 testes, zero falhas**. Corrigido o retorno Unit de um teste JUnit novo. Migrações v1/v2/v3/v4 até v5 e gravação/releitura completa verificadas. Teste Android SQLCipher v5 em execução.
Resultado Android: **3 testes SQLCipherRuntimeTest aprovados** no Pixel 8 / Android 17. Inclui biblioteca JNI, lugar cifrado e histórico digital v5 após fechar e reabrir o arquivo cifrado.

Nova rodada: **24 testes JVM aprovados** (ContextEngineRulesTest 9, ContextEngineIntegrationTest 13, HistoricalPhoneInsightsTest 2). Inclui a divisão dos handlers, exclusão mútua e replaceDay com epochDay derivado.

Rodada após modularizar animações: **26 testes JVM aprovados** (ContextEngineIntegrationTest 13, AnimationBehaviorTest 12, AnimationRegistryTest 1), mais **3 E2E HOME aprovados** no Pixel 8 / Android 17. Build debug/AndroidTest aprovado. Todos os 84 clips preservaram a referência anterior. Corrigida a visibilidade do helper de celular compartilhado com o idle.

Rodada após separar os resolvers e painters: **92 testes pixel em 19 classes, zero falhas**. Inclui cinco cenários diretos dos resolvers e comparação de pixels/âncoras/mapas de partes de 1.017 frames com referência do pintor original. AnimationPlayer adicionado em seguida; sua rodada final e E2E estão em andamento.

Resultado final das fases 22–23: **93 testes pixel em 19 classes, zero falhas**, e **três E2E HOME aprovados** com a máquina/player/painters divididos. Build debug/AndroidTest aprovado. Nova rodada em execução: UiActionTest, PlacePickerViewModelTest e E2E após eventos/erros da Home.

A primeira rodada da Home falhou no cache incremental do KSP (`FileAlreadyExistsException` ao copiar `HoodieApp_MembersInjector.java`), antes de compilar/testar as mudanças. Diretórios gerados e cache debug do KSP limpos dentro de app/build; repetição com `-Pksp.incremental=false` em execução. As alterações de Home/erros ainda não estão verificadas.

Inspecionados visualmente os previews gerados `hoodie_model_sheet.png` e `hoodie_walk_cycles.png`: contornos, detalhes do moletom, vistas, patas, mochila e silhuetas coerentes após a divisão. Essa inspeção do agente não altera metadados de revisão artística por Rafael (fase 36).

## Próxima sequência

Prioridade: concluir UiState + UiEvent e migração de erros/textos (24–26), depois acessibilidade/matriz visual (27–28), snackbar geofence (31), revisão de índices/documentação/versionamento/arte/fonte (33–37), além das verificações pendentes de UI/consentimento. replaceDay deriva epochDay da data quando omitido e sempre substitui sessões; não aceita null. O objetivo integral permanece ativo.
O primeiro build ocorreu enquanto novos arquivos e recursos de permissão eram adicionados e não os incluiu; foi reiniciado com as alterações completas.
Não considerar nenhuma fase concluída apenas com este checklist: conferir fontes, testes e execução real.


## Rodada de UiState / UiEvent em 02/10/2026

- Onboarding agora expõe AppError no estado e OpenAppSettings em canal buffered. A conclusão publica completeOnboarding somente após persistir rotina/nome, preparar engines e agendar os workers; falha mantém o onboarding aberto. Cancelamento é propagado e busy é limpo no finally.
- Diário expõe ShowPlaceDetails com a data de origem, ignorando detalhes enfileirados de outra data. Falha de leitura usa DatabaseError.ReadFailed com texto localizado na apresentação.
- CollectUiEvents centraliza coleta com lifecycle STARTED e callback atualizado. Home/onboarding impedem requisições concorrentes de salvar a localização.
- Corrigidas duas chamadas do fixture at no MobilityGraph que eram ocultadas pelo método local homônimo.
- Rodada JVM atual: UiActionTest, PlacePickerViewModelTest e PhoneDiaryIntegrationTest. Ainda sem resultado; não registrar aprovação até o processo terminar.
- Preparadas em app/build (ainda NÃO aplicadas) migrações de Settings/PhoneInsights para ações com erros tipados/eventos, extração dos textos estáticos das seis telas e semântica/alvos de componentes comuns. Scripts: phone_ui_events.py, settings_ui_events.py, common_accessibility.py, migrate_ui_strings.py. Aplicar somente após a compilação ativa terminar, revisar o diff e executar nova validação.


Resultado da rodada anterior: **28 testes JVM aprovados**, UiActionTest 5, PlacePickerViewModelTest 19 e PhoneDiaryIntegrationTest 4. Main/KSP/Kotlin/Java/Hilt compilados com Onboarding/Diary/Home e erros tipados. Não houve execução E2E nessa rodada.

As quatro migrações preparadas foram aplicadas depois que o Gradle terminou. Migrados 154 textos estáticos para strings.xml; adicionadas mensagens/eventos de Settings e erros tipados no PhoneInsights. runUiAction mantém cancelamentos e erros fatais; Diary agora preserva diagnóstico no log. Atualizadas as três importações depreciadas de LocalLifecycleOwner. Acessibilidade inclui Pixel Lab e PlaceTypeCell.

Nova validação em execução: os mesmos 28 testes JVM, mais OnboardingHomeSaveInstrumentedTest, DiaryControlsTest, PlacePickerScreenTest e CommonAccessibilityTest no emulator-5554 com Vulkan. Ainda não registrar aprovação dessa nova rodada. Sem edição de fontes durante a compilação.

Fonte Press Start 2P e licença SIL OFL 1.1 obtidas do repositório oficial Google Fonts; arquivos temporários em app/build/PressStart2P-Regular.ttf e app/build/PressStart2P-OFL.txt. Integração em res/font e tipografia ainda pendente (fase 37).


## Reintegração autorizada — 02/10/2026

Uma restauração da árvore local removeu entregas ainda não commitadas (ver `stability-restore-audit.md`). O usuário autorizou: “Continue e reintegre o plano completo”. Resultados anteriores continuam históricos; não comprovam automaticamente a árvore atual.

Recuperados no checkout atual:

- **24–26:** erros tipados do PlacePicker, UiState de carregamento de Ajustes com retry, mensagens `UiText` para localização, 110 textos estáticos adicionais e 20 mensagens formatadas, launchers do sistema com falhas recuperáveis.
- **27:** dias da semana como checkboxes de 48 dp com descrição completa; controles do replay com estado e quebra de linha; dismiss de perguntas com PixelButton; busca/resultados e tipo/raio com alvos mínimos.
- **28:** conteúdos de produção separados em cinco telas, matriz de 180 casos e 42 capturas adicionais, relógios e arte determinísticos, MapView offline para fixtures. As referências não foram geradas/aprovadas ainda.
- **30–31:** melhorias atuais de foco preservadas, resultados limitados a 140 dp, geofence em host de Snackbar que permite nova tentativa após navegar sem salvar novamente o lugar.
- **33:** migração v6→v7 e sete índices compostos, schema 7 exportado, teste de preservação e planos SQL. Reexecução pendente.
- **34–35:** docs de regressão visual, índices, fonte e checklist direto na main. Versão dev preservada; RC/estável aguardam gates.
- **37:** Press Start 2P integrada com SIL OFL no APK e no repositório; textos longos continuam com fonte normal. Verificação visual pendente.

Na primeira rodada reintegrada, o código principal compilou; os testes expuseram o helper UiText ausente. Depois da recuperação desse helper, o merge de recursos encontrou três duplicatas idênticas, removidas. Uma nova compilação principal passou; a compilação completa dos testes continua em execução. Nenhuma aprovação de testes ou imagens foi inferida desses builds.

Pendências de conclusão: executar a suíte atual; native E2E, keyboard, acessibilidade e consentimento/reabertura; gerar, inspecionar e comparar toda a matriz visual; verificar navegação; revisar a arte com Rafael; promover versões somente após os gates. O objetivo completo permanece aberto.

### Suíte JVM da árvore reintegrada

Em 02/10/2026, a execução completa de `testDebugUnitTest` produziu **381 testes em 75 classes, zero falhas, zero erros e zero ignorados**. Os XMLs atuais incluem Migration6To7Test (preservação e sete EXPLAIN, schemas 1–6 até v7), Migration5To6Test, SystemActionTest (4 casos), PlacePickerViewModelTest (22 casos), todas as regras digitais/diário/mobilidade/workers e regressões de arte. Consistência de README/versionamento passou. Evidência agregada em `app/build/reintegration-jvm-evidence.json`; relatórios em `app/build/test-results/testDebugUnitTest`. APKs e testes instrumentados ainda em montagem, capturas pendentes.

### Android da árvore reintegrada

APK de app e testes montados com sucesso; `am instrument` concluiu **46 testes, todos aprovados e nenhum ignorado**, no Pixel_8/API 37 com Vulkan. Classes: onboarding HOME, SQLCipherRuntime, DataStorePersistence, PlacePickerScreen, DiaryControls, CommonAccessibility, GeofenceFeedback, UiTextResource e DigitalConsent. O caso `teclado_nao_cobre_o_salvar_nem_o_campo` executou e passou com teclado virtual real. Consentimento: permissão sozinha não habilita; pedido explícito sobrevive à reabertura; retorno concedido habilita e persiste; retorno negado/cancelamento mantém desativado. Evidências em `app/build/reintegration-native-1.log` e `reintegration-native-evidence.json`. Captura piloto em execução.

### Aprovação manual de arte — 02/10/2026

O usuário confirmou: Rafael revisou e aprovou os quatro grupos walk, idle, sleep e work. A fase 36 tem aprovação humana; a sincronização dos dois art-status.json e a validação automática após a alteração continuam pendentes nesta compilação. A galeria docs/art-review.html registra a confirmação e conserva os hashes das fontes apresentadas.


### Validação posterior de estados, navegação e arte

A segunda rodada executou 389 testes: 387 aprovados e dois testes novos de RetryableUiState falharam. O teste de cancelamento passou a cancelar a coleta real, verificando job cancelado, propagação à coleta e ausência de erro de UI. O teste de erro fatal verifica tipo, mensagem e preservação da exceção original na cadeia de causas, aceitando a recuperação de pilha feita pelas corrotinas. A implementação do helper não foi alterada para contornar essas falhas.

Os dois art-status.json foram sincronizados com a aprovação humana dos quatro grupos. A comparação textual e a conferência dos quatro campos passaram. Migrados os rótulos restantes VISITA, agora e uso do celular por contexto para strings.xml. A terceira rodada de suíte completa e montagem de APKs está em execução; resultados ainda pendentes.

Resultado JVM da terceira rodada: 389 testes em 77 classes, zero falhas, erros ou ignorados. Inclui os novos testes de retry e navegação e a conferência dos metadados aprovados por Rafael. Evidência agregada em app/build/reintegration-jvm-evidence-3.json. Montagem dos APKs e execução Android/visual ainda pendentes.


### API 34, lint e piloto visual

A imagem oficial Google APIs API 34 r14 x86_64 foi obtida do catálogo Android, com tamanho 1.563.721.130 bytes e SHA-1 e0f6c9a0691aa27bd597d0deb1bcfdc943ac8ca7 conferidos. O emulador isolado usa 1080×2400, densidade 420 e SwiftShader/OpenGL; a versão API foi confirmada no dispositivo. O Pixel_8/API 37 desta tarefa foi encerrado preservando seus dados.

Lint debug concluiu com sucesso. A suíte Android completa sem a matriz visual executou 72 testes e teve oito falhas: corrida no fechamento/reabertura do DataStore, um timeout de consentimento, três amostras de mapa em fixtures maiores que a área disponível com barras do sistema e três buscas de botões com capitalização incorreta. DataStore recebeu arquivos únicos e cancelAndJoin em ambos os jobs com finally; fixtures do mapa ocultam as barras para acomodar as dimensões declaradas; testes de botões usam o texto exibido em caixa alta. Consentimento isolado passou nos três cenários em 0,318 s; a repetição integral permanece necessária.

Piloto visual API 34: seis casos full/360×640/fonte 1,3 executados e 13 PNGs gerados, incluindo finais da rolagem e mapa. Todos foram inspecionados. O contraste da Home está corrigido e o Diário não travou. Foram identificados horários quebrados, data parcialmente fora do viewport e espaçamento insuficiente nos contadores. Aplicados horários sem quebra, FlowRow no seletor de data e lineHeight/peso normal nos estilos HUD, com separação entre rótulo e duração no resumo. As imagens piloto ainda não são referências aprovadas. Nova montagem em execução.


### Correções de layout e validação API 34

A suíte JVM posterior aos ajustes de fonte/data/horários e ao suporte de nomes RC passou com 390 testes em 77 classes, zero falhas, erros e ignorados. Evidência em app/build/reintegration-jvm-evidence-visual-layout.json.

A terceira rodada Android corrigiu o uso de boundsInWindow recortado na amostragem de pixels do mapa; a origem sem recorte corresponde à camada capturada. Todas as seis dimensões do teste com MapView real passaram. PixelButton agora expõe contentDescription explícita, conferida junto a papel e alvo mínimo. O runner relatou OK (72 tests), mas a auditoria dos códigos individuais identificou um AssumptionViolated (-4) no teste de teclado: resultado efetivo 71 aprovados e um ignorado, zero falhas. Não considerar a rodada integral concluída. O teste foi alterado para solicitar a IME real explicitamente e exigir sua presença, com tempo de espera limitado; ausência agora falha em vez de ser ignorada. Reexecução pendente.

O segundo piloto visual gerou 13 imagens e todos os seis casos passaram. As 13 imagens foram inspecionadas: data completa em duas linhas de controles quando necessário, horários sem quebra, contraste da Home e rótulos/contadores legíveis. A gravação da matriz completa de 180 casos está em execução. A configuração do CI foi alinhada a API 34, perfil pixel_8, 1080×2400/densidade 420, SwiftShader/OpenGL e teclado virtual habilitado.


### Matriz completa e teclado real — validação posterior

Gravação API 34 concluída: 180 casos, 222 PNGs (180 iniciais, 36 finais da rolagem, seis mapas). Todos os nomes, dimensões, opacidade nos cantos e SHA-256 foram conferidos. As 222 imagens foram inspecionadas em 37 folhas de seis capturas em resolução original. Aceitas 198; 24 imagens de Ajustes precisam recaptura porque os pares Permissões/Re-registrar e Acesso ao uso/Apagar histórico quebravam palavras com fonte ampliada. As ações passaram a usar FlowRow sem dividir a largura igualmente. Compilação dessa correção em execução. Referências ainda não foram publicadas nem a comparação automática executada.

O teste estrito identificou um diálogo “System UI isn't responding” sobre a janela do emulador, impedindo foco e solicitação da IME. Recuperado o System UI, o teste de teclado virtual real passou em 6,406 s. A quarta execução integral Android passou nos 72 casos em 142,352 s, com 72 códigos de sucesso e nenhum código de falha ou ignorado. Evidências: app/build/api34-strict-keyboard-4.log e reintegration-native-api34-evidence-4.json. Essa execução precede o ajuste visual dos botões de Ajustes.


Correção de Ajustes compilada em 4m39s. Recaptura executou 30 casos em 70,847 s, todos aprovados. As 24 imagens afetadas foram reinspecionadas em resolução original e aceitas: rótulos inteiros, botões em linhas separadas quando necessário e final da rolagem preservado. As 198 imagens restantes tiveram hashes idênticos. As 222 referências aceitas estão em app/src/androidTest/assets/goldens/screens, com dimensões/hashes e ambiente em docs/golden-reference-manifest.json. A comparação pixel a pixel ainda está pendente; gravação e revisão não substituem essa execução.

