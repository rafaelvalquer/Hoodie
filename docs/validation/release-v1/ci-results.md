# RELEASE-01 — qualidade

Validação local mais recente: **1306 unitários aprovados e lintDebug sem erros**, após a correção de upserts redundantes. [Evidência](unit-results-after-noop.json). Os 1306 unitários também passaram na CI do SHA corrigido; a CI completa verde, os instrumentados completos e os novos artefatos R8 continuam pendentes.

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

## CI do commit `1b2dfe2910ac048c5287ddfc31ecde1e23813dd7`

[Execução 39245887355](https://github.com/rafaelvalquer/Hoodie/actions/runs/39245887355): lint, unit-tests e assemble aprovados. Instrumentation reprovada: **42 casos direcionados, 35 aprovados, sete falhas, zero ignorados**. Os dois testes de desempenho e os três da Loja passaram. A suíte completa não foi iniciada porque a fase direcionada falhou.

As seis falhas de HoodieTouchInteractionTest ocorreram na espera de foco em `awaitTestWindow`, antes de executar as ações verificadas; a sétima foi a verificação de foco antes do IME em PlacePickerScreenTest. A captura final mostra “Pixel Launcher isn't responding”; `dumpsys window` registra ANR do Nexus Launcher às 11:51:57 UTC, antes do início dos testes, e seu diálogo ainda possui o foco ao final. Evidências publicadas: [instrumentation-reports](https://github.com/rafaelvalquer/Hoodie/actions/runs/39245887355/artifacts/12184648701), ZIP SHA-256 `e8827c2d3dde1f84062759134b71eaeca1ae1f8fb60a2002cb8b5fb3949a686a`.

Defeito adicional confirmado no diagnóstico: `adb ... | grep -q ...` sob `pipefail` pode retornar 141 quando o consumidor encontra o ANR e fecha o pipe antes de o produtor terminar. Reprodução com snapshot sintético grande retornou exatamente 141, fazendo o `if` anterior ignorar o bloqueador. O preflight agora lê um snapshot completo de `dumpsys window displays`, sem histórico de ANRs antigos, e reprova foco nulo, ausente ou ocupado por ANR. Cinco testes do verificador passaram; o snapshot real dessa CI é rejeitado e o snapshot atual saudável do emulador é aceito. Não remove nem aumenta os timeouts dos testes de foco.

O emulador da CI recebe explicitamente 3072 MB; as invocações instrumentadas do Gradle usam no máximo dois workers, sem paralelismo e heap de 1 GB, após a compilação prévia. Isso reduz a disputa de recursos, mas não é uma aprovação do novo ambiente: exige outra execução integral. Bash e actionlint aprovaram as alterações. Nenhum retry automático foi adicionado.

Revisão dos gates relacionados: `release-runtime-smoke.sh` também passou a usar snapshots atuais de Activity e Display, sem a seção histórica `lastanr`; exige o applicationId exato da variante, evitando aceitar outro pacote com o mesmo prefixo. Os snapshots de ambas as aberturas ficam nos relatórios. Os sete testes do verificador passaram, incluindo rejeição do prefixo `.homologacao` quando o pacote público é esperado e rejeição de pacote presente apenas em janela sem foco. A listagem de assets do APK também é gravada antes do `grep`, evitando o mesmo encerramento antecipado de pipe.

## CI de `a9a693ac` e persistência da Home

[Execução 39290009748](https://github.com/rafaelvalquer/Hoodie/actions/runs/39290009748): lint aprovado; **1303 unitários, 1302 aprovados, uma falha e zero ignorados**. A falha foi `HoodieInteractionHomeIntegrationTest#enabledGestureProducesOneSequenceAndNeverWritesDiaryOrOtherTables`: os registros antes/depois eram iguais, mas o observer recebeu `Interaction invalidated [hoodie_state]`. Build e instrumentados não iniciaram por dependência. [Relatórios unitários](https://github.com/rafaelvalquer/Hoodie/actions/runs/39290009748/artifacts/12204754445), ZIP SHA-256 `be2c3a07d23b7cb9027a4f7453a25cff2977ba5fe6847c7cfc6fcb315b201991`.

Diagnóstico local: os quatro casos originais passaram numa rodada, e a instrumentação temporária de escritas SQL passou nas três rodadas limitadas seguintes, sem escrita durante a fase observada. Essas passagens não apagam a falha da CI e não provaram qual chamada originou aquela notificação. O rastreamento temporário foi removido; os quatro testes e todas as asserções originais permanecem intactos.

A investigação identificou uma escrita desnecessária verificável: `HoodieEngine.resolve()` fazia upsert de `hoodie_state` mesmo quando o simulador devolvia exatamente o estado já salvo. Uma regressão separada contou **três escritas SQL reais em três resoluções idênticas**, com zero esperado, e falhou antes da correção. O engine agora compara a entidade resultante com a entidade lida e faz upsert somente quando os valores diferem. Continua processando atividades/timeline e calculando necessidades ao vivo; não usa retorno antecipado, não muda schema e não elimina mudanças reais.

Três regressões verificam ausência de escrita/novos registros para estado idêntico, persistência após avanço real do estado e atualização das necessidades visuais sem regravar uma atividade inalterada. Antes do terceiro caso, regras do engine, persistência do simulador, Home e regressão somaram **19 testes aprovados**, sem ignorados. A rodada posterior com os três casos de persistência e os quatro originais da Home passou **7 testes**, sem falhas/erros/ignorados. Uma nova suíte integral e CI do commit corrigido continuam necessárias.

Validação integral local posterior: **1306 testes em 299 suítes, zero falhas, erros ou ignorados**, incluindo os seis exportadores visuais obrigatórios. `lintDebug` também passou; build completo desses dois gates em **13m28s**. [Contagens conferidas por casos reais, hashes dos blobs testados e resumo do lint](unit-results-after-noop.json). Os quatro testes originais da Home não foram alterados. Relatórios XML completos preservados localmente, com hash do ZIP no resumo. Essa aprovação local não substitui os instrumentados nem a CI verde do novo SHA; os artefatos R8 anteriores precisam ser reconstruídos após a mudança de produção.

## CI da correção de gravações redundantes

Commit `f69f6e6b4616d718ecfe297d2719e6786b6293b6`: [execução 39296006327](https://github.com/rafaelvalquer/Hoodie/actions/runs/39296006327). Unitários **1306/1306, zero falhas/erros/ignorados**, lint e assemble aprovados. O job de instrumentação falhou no preflight, antes de executar qualquer teste: o snapshot inicial tinha `mCurrentFocus=null`; o snapshot completo capturado em seguida já tinha o Launcher focado. A captura de energia registra `Awake`; não há ANR indicado no Logcat preservado. Isso identifica uma transição de janela após o boot, sem aprovar a instrumentação ou afirmar estabilidade prolongada do Launcher. [Resumo e hashes](ci-f69-focus-transition.json).

O preflight agora observa a atribuição de foco por no máximo 45 segundos e preserva cada snapshot completo. Um diálogo atual de ANR ou erro de ADB continua reprovando imediatamente; prazo excedido também reprova. Não inicia, repete nem ignora testes nessa espera. Treze testes do gate passaram, incluindo timeout, leitura lenta, falha de ADB e ANR em múltiplos displays. A reprodução dos snapshots reais rejeitou o primeiro e aceitou o segundo. A execução remota do gate alterado e a suíte instrumentada completa ainda precisam passar.

## CI após a espera de foco inicial

Commit `d45dc8983b66c74e777edb5d5ced831393ad53b5`: [execução 39299025528](https://github.com/rafaelvalquer/Hoodie/actions/runs/39299025528). Unitários 1306/1306 sem falhas/erros/ignorados, lint e assemble aprovados; 13 testes do gate de foco também aprovados. O preflight aceitou foco após um snapshot, os 42 suspeitos passaram e a suíte completa executou **388 testes: 378 aprovados, 10 falhos e zero ignorados**. Todos os 208 casos não pertencentes à matriz visual passaram; isso não torna o job ou o gate de qualidade verde. [Contagens e lista exata](ci-d45-results.json).

As dez falhas da matriz visual acusam diferenças de 1–3 pixels em Diary/Digital/Place Picker. O artefato contém os relatórios completos, mas não os PNGs de diff: a configuração UTP confirma `uninstall_after_test: true` e os goldens eram salvos somente no diretório removido do app. A captura foi ampliada para o `additionalTestOutputDir` já recolhido pelo runner antes da limpeza e para registrar as primeiras coordenadas/cores diferentes no erro, sem alterar a asserção de zero pixels. Referência do mecanismo: [Android Developers](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci#save-and-locate-the-files).

Outra diferença verificada é o executável do emulador: produtor local das referências 37.1.11.0/build 15917651, CI 37.2.12.0/build 16428233. A CI agora fixa 15917651 para reproduzir o produtor, com metadados no manifesto das referências. O arquivo oficial Linux respondeu HTTP 200. Isso remove a variação de versão do ambiente, mas não prova a causa nem resolve por si só os pixels divergentes. Nenhuma imagem revisada ou tolerância foi alterada. Nova execução completa continua necessária.

Diagnóstico local direcionado: os mesmos dez casos passaram com comparação exata em 48,689 s no emulador de referência, mantendo os 222 hashes de imagens revisadas. Há, porém, um ANR de SystemUI registrado às 23:15:15.921 antes desses casos e foco atual no diálogo observado depois; não aprovar a estabilidade do ambiente com essa rodada. Uma execução separada apenas de gravação para diagnóstico produziu três PNGs no diretório do runner; a cópia foi recuperada e os pixels conferem com as três referências. Essa gravação não substitui asserções nem altera assets. [Resultado e limites](local-d45-golden-diagnostics.json). A coleta remota UTP e a suíte completa no emulador fixado continuam pendentes.

## CI do ambiente fixado e preparação do SDK

Commit `bd4ff6feb4063ba720a3ed013a00ef94a8504ae4`: [execução 39341180106](https://github.com/rafaelvalquer/Hoodie/actions/runs/39341180106). Unitários, lint e assemble aprovados; instrumentação falhou durante o download de `system-images;android-34;google_apis;x86_64` com erro “closed”, antes de criar o emulador ou executar testes. O executável 15917651 foi baixado/instalado, mas a rodada não valida os goldens no ambiente fixado. [Evidência e ação seguinte](ci-bd4-sdk-download.json).

A preparação do SDK passa a instalar a imagem antes do runner, com até três tentativas de download e prazo de dez minutos, guardando logs de cada tentativa e metadados da instalação. A revisão 14 é exigida para corresponder às referências; o catálogo oficial e a instalação local confirmam essa revisão. A execução dos testes continua única após o SDK pronto. A validação remota desta preparação está pendente.

Correção adicional identificada no round-trip de backup: previsões de rotina idênticas eram removidas/reinseridas, mudando IDs importados. As regressões reproduziram seis operações SQL em três atualizações e troca de ID em atualização real; após a correção, 17 direcionados e a suíte completa de **1309 testes/300 suítes** passaram, sem falhas, erros ou ignorados. O lint local passou com zero erros e 68 avisos; build completo em 8 min 23 s. [Fontes, resultados e limites](routine-persistence-regression.json). CI verde no novo SHA, importação instrumentada do fixture e novos artefatos R8 ainda precisam passar.

## CI de `ecdf8148`

[Execução 39344406307](https://github.com/rafaelvalquer/Hoodie/actions/runs/39344406307): unitários, lint e assemble aprovados. A imagem API 34/revisão 14 foi instalada na primeira tentativa; o erro anterior de download não reapareceu nesta execução. A fase direcionada executou **42 testes: 35 aprovados, sete falhos e zero ignorados**. Desempenho e Loja passaram; seis falhas de carinho ocorreram ao aguardar foco, e a sétima no foco antes do IME. A suíte completa não iniciou, portanto não há nova aprovação dos goldens. [Contagens, falhas e hashes do artefato](ci-ecdf-results.json).

O preflight aceitou o Launcher focado às 00:10:10 UTC, mas o Android registrou um ANR do Launcher às 00:10:19, antes do primeiro teste às 00:11:02. O diagnóstico reportou CPU total 100%, pressão de CPU média de dez segundos 72,86% e pressão de memória 20,17%, no guest de dois cores/3072 MB. O foco momentaneamente saudável não comprovou estabilidade do boot.

O workflow passa a registrar os recursos reais do host e oferecer quatro cores/4096 MB ao guest; Logcat, ANRs e crash do preflight ficam preservados antes da limpeza dos logs de teste. A alteração responde à contenção observada, mas ainda não é uma correção comprovada: exige nova CI completa. Nenhum timeout de teste, asserção, referência visual ou quantidade de casos foi alterado, e não há retry automático dos testes.

## CI de `bf079dee` e entrada antes do foco

[Execução 39416243303](https://github.com/rafaelvalquer/Hoodie/actions/runs/39416243303): unitários, lint e assemble aprovados. Direcionados **42: 34 aprovados, oito falhos, zero erros/ignorados**; a suíte completa não iniciou. Além das sete falhas de foco anteriores, OFFICE falhou em apresentar de fato o gesto no teste de desempenho. [Casos, artefato e recursos](ci-bf079-results.json). O host confirmou quatro CPUs e aproximadamente 16 GB; aumentar os recursos não resolveu o boot.

Os registros novos localizaram a sequência: o runner enviou `input keyevent 82` às 11:52:47.192 UTC, logo após `sys.boot_completed`; às 11:52:47.227 o InputDispatcher já aguardava uma janela focada. O evento KEY foi descartado por falta de foco e o ANR do Launcher foi registrado antes de o preflight aceitar a janela. O diálogo apareceu depois. [Trecho exato do job e do Android](ci-bf079-boot-trace.txt). A inspeção apenas do foco naquele instante não bastava.

A inicialização passa a usar o mesmo emulador oficial/build 15917651 em diretório isolado, com [SHA-256 e origem registrados](linux-emulator-provenance.json), configuração de display definida antes do boot e espera pelo foco real, sem injetar a tecla prematura. ANR registrado no boot bloqueia a execução antes do Gradle. A suíte permanece inteira e os goldens continuam exatos. A regressão de orquestração reproduziu o início indevido do Gradle no script anterior e passou os três cenários após o bloqueio; são fixtures de ADB/Gradle, não testes reais de aplicativo.

O bootstrap é compartilhado pela CI, pelo runtime do release e pelo [diagnóstico R8 em Linux/KVM](r8-development-qa.md). Actionlint e sintaxe foram conferidos; a execução remota da nova inicialização e os gates completos continuam pendentes.

A concorrência preserva a execução Android já iniciada: commits novos aguardam seu próprio run completo, evitando perder uma suíte em andamento durante ajustes de documentação ou do diagnóstico independente. A exigência de CI no SHA do candidato continua igual.
