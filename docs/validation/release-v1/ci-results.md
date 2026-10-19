# RELEASE-01 — qualidade

## Linha de base remota

Commit `6e03a2880f4e5e8e4ea06469fcbfb3e5eb51a891`: [Android CI #77](https://github.com/rafaelvalquer/Hoodie/actions/runs/39174057417), concluída em 18/10/2026. `unit-tests`, `lint` e `assemble` aprovados; `instrumentation` reprovada. Logs remotos confirmam:

- `ScenePerformanceDeviceTest`: Shopping não apresentou a interação dentro da janela do teste.
- `HoodieTouchInteractionTest`: timeout ao encontrar o nó Android de acessibilidade.
- `PlacePickerScreenTest`: Activity RESUMED mas janela sem foco antes do IME.
- `SetupAndDemoUiTest`: host ComponentActivity incompatível com Hilt.
- 180 goldens foram ignorados nessa execução; isso não comprova regressão visual.

## Correções e evidências locais

- Renderizador preservado ao alternar interações; coleta da máscara muda sem descartar sessões. A identidade do Shopping inclui lugar, dia, variante e planta, mantendo iluminação/animação fora da identidade.
- Contadores de criação, descarte e reconstrução; regressão unitária confere continuidade e invalidação por mudança real de lugar.
- Teste de performance aguarda saída/retorno efetivos e a apresentação do visual seguro. Evidência da falha anterior: contagem de apresentações do Shopping permanecia em 431 durante toda a fase safe-pause; o teste estava medindo frames de outra cena.
- Acessibilidade inicializada antes da composição; aguarda frame publicado e procura ações nas janelas acessíveis. Mantém os nomes e as ações Android de toque/carinho.
- Host de teste aguarda Activity RESUMED e janela focada; mantém tela ativa. Captura local anterior mostrou o tutorial de stylus do Gboard cobrindo a UI. Ambiente configura teclado virtual e desativa o tutorial.
- Demo recebe explicitamente a ViewModel de teste, sem alterar a injeção de produção; valida banner, modos, reprodução, pausa e saída na UI atual.
- Exportador visual do Shopping seleciona o comprador pelo brain; a cena também contém o NPC auxiliar e não pode mais pressupor lista de tamanho um.

Rodada inicial corrigida: **37 testes direcionados aprovados** (129,123 s, API 37/16 KB), antes das asserções adicionais de reprodução/pausa e sincronização do Shopping. Não equivale à suíte final completa.

Primeira rodada local com exportadores habilitados: **1303 testes, 1 falha**, causada pelo `.single()` obsoleto do exportador Shopping. Corrigida e validada na rodada final abaixo; a primeira rodada não conta como aprovação.

Sete testes Python do validador ELF/ZIP aprovados, incluindo biblioteca transitiva desalinhada, ELF 4 KB, endereço incongruente e ZIP não alinhado. Workflows verificados com actionlint 1.7.12; scripts verificados por `bash -n`.

O gate JUnit conta os casos reais, falhas, erros e ignorados, e exige correspondência com os totais declarados no XML. Cinco testes do próprio gate passaram, incluindo rejeição de skip/falha ocultados por cabeçalho zerado, contagem declarada sem casos correspondentes e ausência de relatórios. Os 1303 unitários também foram conferidos por essa versão do validador.

## Gate final

Rodada final dos unitários: **1303 testes em 298 suítes, zero falhas/erros e zero ignorados**, incluindo os seis exportadores antes opcionais. Evidência: [unit-results.json](unit-results.json). A regressão instrumentada direcionada do Shopping concluiu **2 testes aprovados**; contadores durante safe-pause/touch mantiveram uma sessão criada e um descarte da saída/retorno legítimos anteriores. Essa execução teve inspeção por depurador, portanto não é benchmark de desempenho.

Build final: `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug`, `bundleHomologacao`, `assembleHomologacao`, `lintRelease`, `bundleRelease` e `assembleRelease` concluídos em **33m21s**. Lint: zero erros (debug 68 avisos, release 56 avisos). R8 aprovado nas duas variantes. O release local não possui chave de upload e seu AAB não é um pacote assinado para a Play.

Execução estrita dos goldens API 34, antes da correção de sincronização: **180 casos, 180 divergências**, sem ignorados; não atualiza referências automaticamente. A Home era capturada antes de chegar o bitmap off Main. O fixture agora aguarda o callback real, avança um frame de apresentação e fixa também a data enviada ao VisualDirector. Captura isolada confirmou a cena visível; ainda difere da referência anterior ao polimento da UI e aos NPCs atuais.

Uma rodada de UI teve a janela “System UI isn't responding” sobre o app; `dumpsys window` confirmou o dono do foco. Essa rodada foi interrompida e não conta como aprovação. Logs e XML preservados localmente. Depois de fechar o diálogo e parar o compilador, a execução é repetida em ambiente livre. CI compila os APKs antes de iniciar o AVD, para reduzir disputa no primeiro boot, e reprova com evidência específica se um ANR do sistema já possuir o foco.

Rodada estável API 34: 36 dos 37 testes UI passaram; a asserção nova de reprodução do Demo revelou espera infinita da infraestrutura Compose enquanto o relógio de reprodução continuava ativo. O teste passou a usar o relógio monotônico injetável já existente na ViewModel, mantendo cliques reais e asserções PLAYING/PAUSED. Validação direcionada posterior: Demo e round-trip rico de backup, **2 testes aprovados**, 46,312 s. Progressão do relógio permanece coberta pelos testes de controlador/ViewModel.

Revisão visual concluída: 222 imagens das seis famílias inspecionadas e referências atualizadas no ambiente API 34 de referência, com hashes em `docs/golden-reference-manifest.json`. Comparação estrita posterior: **180 testes aprovados**, 299,961 s, sem ignorados. Em seguida, os seis fixtures de carregamento do Diário foram corrigidos para fornecer `SectionState.Loading`, regravados e revisados em resolução original; a versão final ainda exige a suíte completa posterior.

Ainda pendente: instrumentados completos no ambiente API 34 com goldens verificados e Android CI verde no SHA final. A pipeline coleta Logcat, screenshot, estado da janela/energia, JUnit/XML e nomes das falhas antes de parar o emulador; rodada direcionada falha bloqueia a suíte, sem retry para aprovação.

Primeira suíte completa de `58f5f214`: **388 testes, 387 aprovados, uma falha, zero ignorados**, em 28m01s incluindo preparação do Gradle. Os 180 goldens finais passaram, incluindo o carregamento do Diário. As onze cenas de desempenho passaram; [CSV com 99 medições](measurements/scene-performance-api34.csv) e [ambiente](measurements/scene-performance-api34-environment.txt). No Shopping e Store, return/safe-pause/touch mantiveram criação=1, descarte=1 e reconstrução=1, correspondentes à saída/retorno legítimos anteriores. O descarte adicionado nesta entrega instrumenta as sessões de Shopping/Store; não usar zeros das outras cenas como prova de que nenhuma sessão foi descartada. Medições em emulador SwiftShader não homologam desempenho/bateria de aparelhos físicos.

Falha adicional: `StoreSceneDeviceTest#manualStoreSurvivesNavigationAndRecreationAndKeepsOtherVenuesDistinct`, ao aguardar “Ajustar atividade” depois da recriação (linha 152 chama o helper, linha 73 aguarda o item do menu no código daquela rodada). Logs/XML/HTML preservados em `app/build/reports/release-v1/first-full/`. A reprodução capturou a Home focada, botão dentro da tela e callback de abertura recebido, mas sem menu persistente. Foco/desenho da janela isoladamente não resolveu a falha. Os diagnósticos limitaram repetições e pararam na primeira falha; execuções que passaram durante a investigação não foram usadas como aprovação.

Causa de sincronização: a cena guardada no singleton pode corresponder à tela anterior, enquanto `onHomeStarted` ainda reconcilia dados e emite `reloadHome`; o estado de carregamento remove temporariamente o menu. O teste agora aguarda `home_context_status`, que só é renderizado com o contexto canônico reconciliado, antes de abrir o menu. Mantém os cliques reais, asserts de cenas/dados, navegação, recriação e os mesmos timeouts. Diagnóstico de falha (semântica, screenshot e janela) usa o diretório UTP para sobreviver à limpeza do runner. Não ficaram logs temporários ou alterações dessa investigação no código de produção.

Validação após essa correção: **12 testes aprovados, zero falhas/erros/ignorados**, incluindo Loja e seus predecessores ScreenState/SetupAndDemo, em 2m18s incluindo build. [Resumo conferido por casos reais do JUnit](store-ui-results.json). O teste da Loja também entra na fase direcionada da CI antes da suíte completa. Essa validação não é apresentada como uma nova aprovação local dos 388 casos: a aprovação integral continua condicionada à CI completa do SHA candidato.
