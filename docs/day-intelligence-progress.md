# Day Intelligence — implementação e gates

Objetivo: interpretar a rotina com evidências explicáveis, reconhecer incerteza e aprender com correções canônicas. Plano recebido em 07/10/2026.

## Sequência obrigatória

0. Estabilização: unit tests, lint, assemble e instrumentação aprovados antes de introduzir DayState.
1. ConfidenceScore, evidências, decisões e política de perguntas.
2. DayState separado de UserContext, reutilizando WakeDetector e SleepOnsetDetector; snapshot persistente e Flow.
3. Correção do Diário em transação: dados canônicos, auditoria, timeline e derivados; usuário vence inferência.
4. Rotina aprendida separada da configuração explícita; janela de 28 dias, mediana/MAD e correções ponderadas.
5. Transporte determinístico com agregados sem trilha GPS, padrões, hysteresis e segmentos multimodais.
6. Migração aditiva v7→v8, integração de um dia completo, UX e ativação ordenada por flags.

## Implementação atual — 07/10/2026

**Implementação concluída e flags ativas.** Validação local: suíte completa com 793 testes JVM (zero falhas, seis exportadores opcionais ignorados), 109 testes funcionais Android, lint sem erros e APK debug. Revalidação da versão final: 100 testes JVM e um teste Android do editor, todos aprovados; lint e APK novamente aprovados. Evidência consolidada em [day-intelligence-validation.json](day-intelligence-validation.json).

- Branch atual: `codex/day-intelligence-foundation`; fase zero registrada no commit `1cc3598e`.
- Confiança unificada, estado do dia persistente, formulário de correção, auditoria transacional, recálculo digital, aprendizado diário e transporte integrados. Cinco flags ativadas na ordem prevista, após os respectivos testes focados.
- Room v8 exportado, quatro tabelas adicionadas sem substituir a rotina manual. Testes verificam caminhos v1–v7 e preservação de dados.
- Correções invalidam o cache do Diário; Jornada e Relógio continuam derivados da mesma fonte. Viagens provisórias com confiança de pelo menos 60% também entram nessa história.
- `day-intelligence-full-validation.log`: 791 testes, 21 falhas em expectativas antigas de perguntas/provisório e contagem do README, seis exportadores opcionais ignorados. O teste novo do dia completo passou. Lint: zero erros, 70 avisos; assemble aprovado. Esta rodada não é aprovação final.
- Ajustes posteriores: proteção contra flap/enriquecimento de evento corrigido; padrão de transporte confirmado antes da chegada; limpeza de padrões ao apagar histórico; auditoria do transporte invalidado por correção de contexto; migração com lugares/contextos/viagens e reabertura do banco. Revalidação em `day-intelligence-integration-validation.log`.
- Reexecução focada: 98 testes; um auxiliar consultava apenas viagens confirmadas ao conferir uma viagem provisória encerrada. Consulta de fixture corrigida para o histórico canônico do Diário.
- `day-intelligence-final-gates.log`: suíte JVM completa com 793 testes, zero falhas/erros e seis exportadores opcionais ignorados; lint zero erros/70 avisos; APK aprovado. 109 testes funcionais Android aprovados no Pixel_8/API 37, excluindo a matriz de screenshots cujo ambiente de referência é API 34.
- O E2E agora inclui academia e ônibus de retorno, além das transições reais via coordinator: despertar/atividade/deslocamento/chegada/desaceleração/sono. Cenário aprovado na suíte completa.
- Ajuste final de aprendizado: corrigir o destino rejeita o padrão no destino original e confirma no novo destino. Suítes afetadas revalidadas e APK recompilado na rodada final.
- `day-intelligence-release-check.log`: BUILD SUCCESSFUL; 100 testes JVM, zero falhas/erros/ignorados; DiaryCorrectionUiTest aprovado com horários contendo segundos/milissegundos; lint zero erros/70 avisos; assembleDebug aprovado.
- Correção de deslocamento inexistente encerra o movimento e não alimenta o aprendizado. Encerrar o último trecho também encerra a viagem. Perguntas supersedidas são canceladas no banco e suas notificações são canceladas somente após o commit da transação.
- Configurações do emulador conferidas ao terminar: `show_ime_with_hard_keyboard=0`, `stylus_handwriting_enabled=null`, iguais aos valores originais.
- Limitação de validação: matriz de screenshots API 34 não executada no AVD API 37; revisão artística permanece PENDING. As três baselines técnicas autorizadas pertencem à fase zero e não constituem aprovação artística.

## Estado inicial

- Árvore de trabalho limpa, branch main; banco Room v7.
- Detectores de despertar/sono, TransactionRunner e fontes canônicas já existem.
- Gate local iniciado com `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- A primeira tentativa não iniciou Gradle por acesso negado ao lock do wrapper no perfil do usuário; repetição com permissão de execução solicitada.
- Emulador conectado encontrado: emulator-5554. A instrumentação deverá usar o dispositivo disponível e registrar versão/limitações.
- Não considerar registros históricos de validação como resultado da árvore atual.
- Branch de trabalho: `codex/day-intelligence`. Plano original preservado em `day-intelligence-plan.md`.
- `assembleDebug` aprovado nesta execução; unit tests e lint ainda ativos. Diagnóstico do daemon confirma análise Kotlin/lint em execução, sem evidência de deadlock.
- Dispositivo conectado usa API 37; não equivale ao ambiente API 34 das baselines visuais.

## Critérios de aceitação

- Transições inválidas bloqueadas e timestamps antigos não fazem rollback.
- Score finito entre 0 e 1, decisão contextual e cooldown por candidato/sessão.
- Correção atualiza a origem observada por Diário/Jornada/Relógio, sem apagar o original auditado.
- Configuração explícita não é substituída pelo aprendizado.
- Classificação não oscila; nenhuma rota GPS é persistida.
- Testes e migração verificam comportamento e preservação dos dados existentes.

## Evidência necessária por entrega

| Entrega | Implementação e verificação esperadas | Estado |
| --- | --- | --- |
| Estabilização | Relatórios atuais de JVM/lint/assemble/instrumentação; corrigir regressões antes de DayState | Aprovada — evidência abaixo |
| Confiança | ConfidenceScore e bandas; EvidenceEngine/DetectionEvidence; decisões 45/60/85%; precedência de fontes; QuestionPolicy 30 min e uma pergunta de transporte por sessão | Implementado e validado |
| Estado do dia | DayState/Reason/Snapshot; máquina restrita; Wake/Sleep existentes; coordinator persistente e Flow; retomada após morte do processo | Implementado e validado |
| Correção | Formulário contexto/lugar/início/fim/transporte; DiaryCorrectionService + auditoria em TransactionRunner; timeline e derivados atualizados; impedir sobrescrita | Implementado e validado |
| Rotina | Modelo separado da rotina manual; 28 dias com decaimento; mediana/MAD; exclusão de exceções/dias incompletos/contradições; peso manual; execução diária | Implementado e validado |
| Transporte | FeatureBuilder sem rota GPS; classifier explicável; padrões de origem/destino/dia/horário; confirmação e rejeição; hysteresis 15 pontos por 30–60 s; multimodal | Implementado e validado |
| Banco | Quatro tabelas novas; migração 7→8 aditiva, índices e schema exportado; preservação de contextos, lugares, mobilidade e histórico digital | Implementado e validado |
| Integração e rollout | Flags inicialmente desligadas, ativação Confidence→DayState→Corrections→Routine→Transport; UI e uma história canônica compartilhada | Implementado e validado |

Suítes requeridas: ConfidenceEngineTest, DayStateEngineTest, DiaryCorrectionIntegrationTest, RoutineLearnerTest, TransportClassifierTest e FullDayIntelligenceIntegrationTest. A suíte do dia completo deve conferir Context/Place/Mobility/Confidence/Diary/Journey/Clock/aprendizado, incluindo correção de almoço e segmentos caminhada→ônibus→caminhada.

## Fase zero — primeira rodada

- `stabilization-gates.log`: 745 testes JVM, 11 falhas, cinco exportadores opcionais ignorados; assembleDebug aprovado. Execução terminou com falha, portanto não constitui gate verde.
- README atualizado para as 143 animações existentes.
- Testes atualizados para postura SIT_FRONT, preservação de seatHip, ocupante do carro fixo no assento e speechLineAt do brain canônico do restaurante.
- Fingerprint v1 de âncoras estabilizado sem regravar os 1.335 hashes do pintor procedural; comparação mantém pixels e ownership semântico.
- Teste de vibração observa um ciclo completo, sem depender de uma única fase da waveform.
- Rodada focada posterior: 52 testes, quatro falhas. Geometria, compatibilidade, fingerprint e fala passaram. Restam três conjuntos de goldens e ausência funcional de vibração nos suportes do trem V2.
- Render do trem V2 corrigido para aplicar o perfil de vibração aos suportes pendulares; reexecução pendente. Com vibration NONE ou perfil ausente, o render permanece igual.
- Candidatos visuais, hashes PNG e evidência dos testes em `day-intelligence-stabilization-review/`. Referências preservadas; autorização solicitada ao usuário para atualizar apenas baselines técnicas, mantendo revisão artística PENDING.
- Lint e APK AndroidTest da execução focada ainda aguardam término. Não promover fase zero até concluir os quatro gates.
- Nova rodada `stabilization-android-gates.log` com `--continue`: sete testes de TransportVisualRegistryTest aprovados; exportTransportGallery divergiu somente em train_day/train_night após corrigir vibração. APK debug recompilado. Lint e instrumentação em execução (sessão de terminal 25239).
- A revisão visual identificou obstrução do Hoodie pelo passageiro central do trem. Passageiro movido para o corredor em `AmbientNpc.kt` e adicionada verificação de ocupação do assento. Essa última alteração ainda precisa compilação e reexecução; imagens do trem na galeria serão atualizadas antes de aceitar hashes.
- Instrumentação iniciada no Pixel_8/API 37: 279 casos previstos. Contagens parciais não equivalem a gate aprovado; extrair relatório final e distinguir funcionais dos goldens condicionais.
- Nenhum motor Day Intelligence foi introduzido antes da estabilização. A próxima ação é concluir a sessão 25239, validar o layout do trem e refrescar os candidatos preservados; a atualização das três baselines técnicas originais aguarda resposta à pergunta de revisão.
- Instrumentação encontrou três falhas de PixelLabNpcTest: preview fora do viewport (bitmap de tamanho zero), toque de comparação fora da área visível e WALK não exibido após um swipe fixo. Testes passaram a usar performScrollTo + assertIsDisplayed para interagir/capturar os controles reais; validação posterior pendente.
- Teste estrito de teclado encontrou show_ime_with_hard_keyboard=0 no AVD, enquanto a CI exige 1. Valor original registrado em `emulator-settings.json` e ambiente alinhado à CI para repetir o teste. Restaurar para 0 após finalizar a validação.
- Teste de grade de lugares usava nove tipos em três colunas, mas o catálogo atual possui dez opções físicas em duas colunas. A verificação agora compara todos os retângulos em coordenadas de conteúdo, compensando a rolagem e sem pressupor quantidade de colunas. Mantidos os checks de toque/visibilidade e separação do mapa.
- Teste de seleção de restaurante na Home recriava mutableStateOf em recomposições e deixava o relógio da animação contínua avançar automaticamente. Fixture corrigido com remember, relógio controlado e rolagem até os controles reais; repetir seleção e assert contexto/cena após salvar.
- Situação ao fim desta rodada de trabalho: instrumentação da sessão 25239 ainda ativa, seis falhas funcionais na árvore anterior às últimas correções. Não reiniciar enquanto a sessão estiver viva. Após terminar, usar `--continue --max-workers=4` para validar JVM de transporte/NpcArtV3 e repetir PixelLabNpcTest, PlacePickerScreenTest e PlaceTypeSelectorIntegrationTest, além de lint. Os candidatos V3/cenas novos devem ser refrescados e revisados antes de qualquer atualização autorizada.
- As 30 imagens preservadas da revisão inicial tiveram hashes novamente conferidos: nenhuma foi alterada silenciosamente. A imagem do trem ainda é anterior à correção de obstrução; não aceitá-la como referência final.
- Sessão 25239 concluída: BUILD FAILED em 24m16s. XML consolidado: 99 testes funcionais (93 passaram, seis falharam), 180 goldens ignorados. Relatório original e resumo por classe preservados em day-intelligence-stabilization-review/android-before-ui-fixes*. Lint encontrou SuspiciousIndentation em AmbientNpc e estado Compose sem remember; corrigidos com blocos explícitos e fixture já atualizado. Rodada focada iniciada na sessão 48263 (stabilization-repairs.log), com quatro workers, três classes Android, transporte/V3/export, lint e assemble. Nenhuma referência visual foi promovida.
- Rodada 48263: oito testes de transporte aprovados, incluindo proteção do assento. Imagens train_day/train_night revisadas após mover o passageiro; somente esses dois hashes de transport-scenes-v1 foram atualizados conforme o fluxo de revisão técnica do exportador. Imagens corrigidas preservadas separadamente; candidato V3 refrescado, mas suas referências e manifest artístico permanecem intactos. Reexecução do exportador pendente.
- Rodada 48263 terminou após interrupção deliberada da instrumentação presa no primeiro caso PixelLab; não é gate verde. Lint atualizado aprovado: zero erros/67 avisos. Fonte Compose 1.11.3 confirma performScrollTo repete ScrollBy enquanto coordenadas não mudam, sem avançar relógio manual. PixelLab passou a fazer até 12 rolagens semânticas com avanço explícito entre tentativas e Timeout de 60s; validação pendente. Diagnóstico isolado em sessão 12250 ainda usa versão anterior do helper.
- Sessão 15967 concluída: exportTransportGallery aprovado com referências revisadas, 29 testes Android executados, 25 passaram/quatro falharam. Troca de espécie e grade de dez lugares passaram. PixelLab controles: margem de rolagem passou a excluir 32dp das barras do sistema; seleção Home buscava texto sem uppercase usado pelo PixelButton, corrigida; showSoftInput passou a usar o View efetivamente focado. Reexecução de seis casos na sessão 85528 (log stabilization-ui-final-repairs.log).
- A rodada 85528 ainda falhou nos quatro casos, relatórios preservados em android-edge-fix.xml. Diagnóstico de coordenadas confirmou BottomSheet fora da raiz durante entrada. Helper passou a limitar viewport à raiz real e aguardar animação; controles do PixelLab e Home agora acionam OnClick semântico de elementos visíveis. Rodada 34564: quatro casos PixelLab aprovados, seleção Home em execução. Teste IME passou a ler WindowInsets.ime real na composição (mesma fonte de imePadding), evitando leitura de insets consumidos no DecorView; ainda precisa validação.
- Sessão 34564 concluída BUILD SUCCESSFUL: cinco testes instrumentados aprovados (quatro PixelLab e seleção de restaurante Home), relatório XML preservado em android-pixellab-home-passed.xml. Iniciada sessão 26354, teste estrito de teclado com insets reais Compose e lint atualizado, log stabilization-ime-insets.log.
- Sessão 26354: teste IME continuou falhando em timeout mesmo lendo WindowInsets.ime real do Compose. Não transformar falha em skip nem aceitar teclado fictício. Adicionada captura UiAutomation e estado real de insets/foco no timeout para distinguir teclado ausente/floating/insets da janela; diagnóstico seguinte pendente. Cinco testes de PixelLab/Home já têm relatório verde; o gate completo permanece pendente.
- Lint da sessão 26354 concluído novamente com zero erros/67 avisos. Diagnóstico visual de teclado iniciado na sessão 80601 (stabilization-ime-diagnostic.log); verificar esse handle antes de qualquer nova execução. Captura esperada no arquivo externo do app stabilization-ime-timeout.png, puxar com adb e inspecionar caso a falha se repita.
- Diagnóstico 80601 concluído com captura: Gboard exibiu tutorial Try out your stylus/handwriting, em vez de teclado convencional, explicando insets zero. Imagem ime-timeout.png preservada. Setting secure stylus_handwriting_enabled original ausente/null, temporariamente definido 0; restaurar com settings delete após validação. Foco do campo do teste passou a usar RequestFocus semântico para evitar gesto de caneta do emulador; todas as verificações reais de teclado/campo/Salvar permanecem.
- Sessão 51227 concluída BUILD SUCCESSFUL: teste estrito de teclado passou com teclado convencional real; campo e Salvar acima do IME. Relatório android-ime-passed.xml preservado. Consolidar suíte JVM completa, lint/assemble e 99 testes funcionais Android; ScreenGoldenMatrixTest excluído dessa repetição funcional porque seus 180 casos já foram classificados como condicionais e não verificáveis no AVD API37 (referência API34 indisponível). Não alegar aprovação pixel a pixel desses goldens.
- Consolidação em execução na sessão 30844 (stabilization-consolidated.log): suíte JVM completa, lint, assemble e toda instrumentação funcional sem ScreenGoldenMatrixTest. Não iniciar rodada concorrente; consultar esse handle. Oito testes de transporte, galeria de transporte e seis casos Android originalmente quebrados já têm evidência focada verde. Settings temporários show_ime_with_hard_keyboard=1 e stylus_handwriting_enabled=0 ainda precisam restauração após essa consolidação (originais 0 e null). Manifest das três imagens corrigidas do trem em corrected-transport-manifest.json.
- Consolidação 30844: JVM completo 746 testes, três falhas (NewPlaceScenes/NpcArtGolden/NpcArtV3Golden), cinco exportadores opcionais ignorados; resumo consolidado por suíte preservado. APK aprovado. Android funcional encontrou timeout novo em DigitalConsentTest.deniedReturnAndCancelledRequestStayDisabled. Fixture setMode finalizava comando shell sem aguardar invalidação de cache AppOps no processo; adicionada espera pelo modo real esperado antes de construir/acionar ViewModel. Assertions de consentimento e persistência preservadas; reexecução pendente após terminar a consolidação.
- Sessão 30844 concluída BUILD FAILED em 14m26s: JVM 746 testes/três falhas visuais/cinco skips opcionais; instrumentação funcional 99 testes/uma falha/zero skips (98 passaram); lint zero erros/67 avisos; APK passou. XML Android preservado em android-consolidated-before-consent-sync.xml. Classe DigitalConsentTest inteira + lint em execução na sessão 13427, log stabilization-consent-sync.log, para validar espera pelo AppOp efetivamente observado; hipótese ainda não comprovada.
- Sessão 13427 concluída BUILD SUCCESSFUL em 3m24s: três testes DigitalConsentTest aprovados e lint aprovado, zero erros/67 avisos. XML android-consent-sync-passed.xml preservado. Repetir toda a instrumentação funcional para comprovar gate após a correção; JVM permanece pendente somente das três referências SHA que aguardam autorização humana.
- Reexecução integral dos 99 testes funcionais Android iniciada na sessão 43198 (stabilization-functional-final.log). Verificar esse handle antes de nova execução. Após terminar, preservar XML, restaurar os dois settings do emulador (IME original 0, stylus original null), e consolidar os gates. Não houve resposta humana à revisão das três baselines; referências V1/V2/V3 originais e manifest PENDING continuam intactos.

## Consolidação e bloqueio da fase zero

- Sessão 43198 concluída BUILD SUCCESSFUL em 11m56s: 99 testes funcionais Android, zero falhas/erros/skips. XML e resumo preservados em android-functional-final-passed.xml e android-functional-final-summary.json.
- Lint aprovado, zero erros/67 avisos; APK aprovado. Suíte JVM completa: 746 testes, três falhas visuais e cinco exportadores opcionais ignorados. As únicas suítes vermelhas são NewPlaceScenesTest, NpcArtGoldenTest e NpcArtV3GoldenTest.
- Os 180 casos condicionais ScreenGoldenMatrixTest não foram revalidados pixel a pixel: o AVD disponível é API37 e a referência exige API34. Esta limitação permanece explicitamente registrada, sem promover capturas como baselines.
- Configurações temporárias do AVD restauradas e conferidas: show_ime_with_hard_keyboard=0; stylus_handwriting_enabled ausente/null. Registro emulator-settings.json marcado restored=true.
- Bloqueio persistente: a pergunta sobre promover os três conjuntos SHA técnicos continua sem resposta humana. NpcArtV3GoldenTest exige aprovação humana para atualizar referências; npc-art-v3-review-manifest.json continua PENDING. A revisão concreta está em day-intelligence-stabilization-review/review.md, incluindo o trem corrigido.
- Como o plano exige gate JVM verde antes dos novos motores, a próxima fase depende dessa decisão. O objetivo integral permanece incompleto: confiança, estado do dia, correções, aprendizado de rotina/transporte, migração 7→8, integração e rollout continuam pendentes. Não há processo de teste ativo nem outra correção funcional comprovadamente necessária após a consolidação.
- Usuário autorizou continuar após a pergunta concreta de atualização das três baselines técnicas. Aprovação artística integral permanece PENDING. Estado atual mudou para main d21cfa43 (novas cenas layered e UI), limpo; criada branch codex/day-intelligence-foundation a partir desse estado. Regeneração visual focada em execução; não promover candidatos antigos sem confrontar o render atual.
- Regeneração atual concluída: 23 testes/três divergências previstas; galeria de transporte passou. Conferidas imagens atuais NewPlace, comparação Dog Worker e cenas V3 (transportes layered). Promovidos exclusivamente os três SHA técnicos com autorização do usuário; manifest artístico mantido PENDING. Captura current-v3-scenes.png preservada. Executar gates completos do estado atual antes dos motores.
- Gates atuais JVM/lint/assemble concluídos BUILD SUCCESSFUL: 767 testes, zero falhas/erros, seis exportadores opcionais ignorados; lint zero erros/70 avisos; APK recompilado. Iniciar connectedDebugAndroidTest completo (incluindo goldens condicionais, sem atualizar referências API34) e restaurar AVD em finally.
- Instrumentação atual (sessão 78554) terminou a parte funcional com três falhas de fixture: PlacePickerScreenTest edição/resumo e troca de tipo ainda buscavam emojis textuais, substituídos no produto por ícones; seleção Home buscava label sem uppercase usado por PixelButton. Atualizadas consultas para o texto real e rolagem dos campos. Reexecução pendente; nenhum DayState introduzido. Casos condicionais API34 seguem SKIPPED no AVD API37.
- Fase zero aprovada na base atual: JVM 767/zero falhas (seis exportadores opcionais ignorados), lint zero erros/70 avisos, assembleDebug aprovado. Instrumentação completa: 105/108 funcionais aprovados inicialmente; os três restantes foram corrigidos e aprovados na sessão 18433, BUILD SUCCESSFUL em 1m7s, XML android-current-fixture-passed.xml. Os 180 goldens condicionais API34 permanecem ignorados no AVD API37, sem verificação pixel a pixel. AVD restaurado: IME 0, stylus null. Aprovação humana das três baselines técnicas aplicada; revisão artística permanece PENDING.
