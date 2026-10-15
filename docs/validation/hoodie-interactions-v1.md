# Homologação — Interações com o Hoodie V1

> Registro histórico da validação de 14/10/2026. A revisão de 15/10/2026 restabelece a flag inicialmente desativada e o prazo pendente de 2 segundos conforme o plano recebido. Os resultados dessa revisão estão em [hoodie-interactions-plan-2026-10-15.md](hoodie-interactions-plan-2026-10-15.md).

Status: **concluído; flag ativada e APK instalado no Pixel 8 emulado**. Gates de testes, interface, acessibilidade, build e lint aprovados; testes físicos dispensados pelo usuário.

Em 14/10/2026, o usuário informou que dispõe apenas do emulador e autorizou continuar sem testes em aparelho físico. A homologação física foi dispensada para esta entrega; nenhuma evidência deste relatório deve ser apresentada como teste físico.

Base de implementação inspecionada: `00f1ab55` (main local, com melhorias de iluminação já presentes).

## Evidências atuais

- Catálogo: 110 regras, 108 frases distintas; regras para Casa, Escritório/home office, Restaurante, Academia, Passeio, Mercado, Loja, Escola, Família, passageiros parados e fallback.
- Testes adicionados: `HoodieInteractionResolverTest`, `HoodieInteractionPolicyTest`, `HoodieInteractionHistoryTest`, `HoodieInteractionCatalogTest`, `HoodieInteractionHitTestTest`, `HoodieInteractionSessionTest`, `HoodiePettingIntegrationTest`, `HoodieInteractionResumeTest` e instrumentado `HoodieTouchInteractionTest`.
- Validação completa final em 14/10/2026: **1.195 casos JVM, zero falhas, zero erros e seis ignorados**. Inclui os dois casos de InteractiveSpeechState, a preservação de LONG_PRESS nas respostas silenciosas e a precedência de espera/descanso sobre mesa genérica no Restaurante. As seis exclusões são exports visuais anteriores à V1; nenhum teste novo de interação foi ignorado.
- **Build debug, APK de instrumentação e lint finais aprovados**, em `build/interaction-final-speech-jvm-gates.log` (`BUILD SUCCESSFUL`, 16m42s). A primeira tentativa anterior falhou na renomeação de um workspace temporário do cache Gradle no Windows, antes dos testes; a reexecução preservou o cache e passou.
- `git diff --check` passou; avisos de conversão LF/CRLF não são erros de whitespace.
- ADB: emulador `emulator-5554` disponível; nenhum aparelho físico conectado nesta etapa.
- Pixel 8: **21/21 testes aprovados** em `build/interaction-final-device-matrix.log` (90,562 s): nove de gestos/acessibilidade e 12 combinações de tamanho, fonte e postura. Inclui cancelamento da pressão ao trocar de cena/atividade, necessidade de novo gesto após exercício e tolerância à respiração sem movimento do dedo.
- A primeira matriz falhou porque `home_live_scene` e `hoodie_interaction_target` estavam no mesmo nó Compose, sobrescrevendo a tag. O alvo passou ao Canvas filho; as asserções de visibilidade foram preservadas e as 12 combinações passaram.
- As 24 capturas finais de cena/contexto foram copiadas para `build/interaction-final-device-review` e a visão conjunta `matrix-overview.png` foi revisada. Balão legível em 100%/130%, cena visível e contexto alcançável por rolagem, inclusive em 360×640 dp. A matriz usa layout real com tempo fixo; não substitui teste da reprodução completa.
- `build/interaction-review/contact-sheet.png` foi regenerada e revisada: antes, PET_HEAD, PURR, NUZZLE e retomada em Casa, Escritório/caneca, Restaurante/mesa e Loja/roupa. Posição, apoio e objetos preservados. Os testes verificam também postura sentada de frente e ausência de repetição de eventos de página/comida.
- `HoodieInteractionHomeIntegrationTest` usa a ViewModel e Room reais: captura todas as tabelas e monitora invalidações durante gesto, reserva, início e cancelamento. Os dois casos, flag ligada/desligada, passaram com **zero alterações e zero invalidações**.
- Instrumentação antiga: há diagnóstico e relatório anterior de CI verde em `stabilization-acceptance-audit.md` (329 casos, zero falhas, 180 capturas ignoradas). A revalidação foi feita em applicationId separado `com.hoodie.app.interactionvalidation`, pois testes antigos de Loja escrevem contexto e histórico. Nenhum dado do app principal será apagado para esse gate.
- `HoodieInteractionContinuityDeviceTest`: **3/3 cenários de reprodução ao vivo aprovados** pelo Canvas e Flow reais: caneca, comida e roupa; `build/interaction-live-continuity-device-controlled.log`, 30,885 s. Verifica início único, todas as três animações de carinho, posição, orientação, postura, flags dos objetos e retomada da animação original.
- A primeira execução desse teste ao vivo falhou (início ausente no Escritório e timeout de ociosidade Compose nos outros dois cenários). O avanço automático do relógio do teste concorria com o Canvas que renderiza continuamente. O teste agora dirige os frames explicitamente, com o relógio real de produção e as mesmas asserções de início/continuidade; nenhum renderer, evento ou objeto foi substituído por mock.
- TalkBack: **1/1 teste aprovado com serviço efetivamente ativo** (`requireTalkBack=true` e UiAutomation sem suprimir serviços). Verifica o serviço `com.google.android.marvin.talkback` na lista ativa, as duas ações nomeadas do Android, toque e carinho, cada uma produzindo exatamente um gesto. Reexecução final: `build/interaction-final-talkback-device.log`, 15,107 s. Configurações originais restauradas e conferidas (`enabled_accessibility_services=null`, `accessibility_enabled=0`). Isso comprova a ação com TalkBack ativo; navegação gestual humana não foi realizada e os testes físicos foram dispensados pelo usuário.
- Revisão final corrigiu a prioridade da regra genérica de mesa no Restaurante (80 → 40), permitindo que espera/descanso (50) produza PET_HEAD → PURR. A regressão também exige PET_HEAD → SMILE ao comer sentado, após o limite seguro. A suíte unitária final aprovou esse ajuste.
- Instalação separada final: assemble debug/AndroidTest e lint passaram em `build/interaction-sandbox-final-gates.log` (4m17s). AVD confirmado como `Pixel_8`, API 37, 1080×2400 px, densidade 420. A rodada completa e a aprovação posterior dos seis casos corrigidos estão documentadas abaixo.

### Regressão instrumentada completa e diagnóstico

A rodada completa terminou em `build/interaction-full-instrumentation.log` (26m08s): **363 casos, seis falhas, zero erros e 180 capturas ignoradas**. Os XMLs e HTMLs foram preservados em `build/interaction-full-instrumentation-first-results` e `build/interaction-full-instrumentation-first-reports` antes de novas execuções. O runner contou 543 eventos por contabilizar novamente as 180 assumptions; esse valor não representa 543 casos. **Os 24 casos novos de gestos, matriz e continuidade passaram** nessa rodada.

| Caso anterior | Evidência e tratamento | Estado |
| --- | --- | --- |
| `LabelSpacingTest.homeYouPanel`, fontes 100%/130% | Procurava o título removido `HOODIE · AGORA`; passa a medir `VOCÊ` acima de `Em casa`, conservando a desigualdade de não sobreposição | Classe completa aprovada: 8/8 casos, incluindo ambas as fontes |
| `PlaceTypeSelectorIntegrationTest` — Restaurante | Fixture implementava apenas `correctContext`; Home atual utiliza `quickCorrect` com identidade do evento. Os dois callbacks agora compartilham a mesma atualização, e o caminho rápido verifica evento, escopo e placeId | Caso aprovado na reexecução final |
| `StoreSceneDeviceTest` — fluxo manual | Loja/Mercado não pertencem às cinco opções rápidas. O teste agora abre `home_correct_see_all` antes de escolher tipos fora dessa lista | Fluxo completo aprovado, incluindo navegação, recriação e distinção entre estabelecimentos |
| `PlacePickerScreenTest` — opacidade | A rodada após recuperação do AVD aprovou a mesma asserção de alpha/cor, sem modificar o campo; controle inteiro é colocado na viewport antes da medição | Aprovado novamente na reexecução final |
| `PlacePickerScreenTest` — Cinema | Captura e árvore em `build/interaction-picker-diagnostics/type-CINEMA.*` mostram chip cortado pela viewport: bounds y=714,3..726,1 dp (11,8 dp). Busca lazy localizava o item da grade antes de tornar a célula inteira visível. O teste executa `performScrollTo()` na própria célula antes de medir altura e sobreposição | Aprovado, preservando altura mínima, não sobreposição e separação do mapa |

Reexecuções finais: `build/interaction-old-failures-final-targeted.log`, **4/4 casos aprovados** (73,376 s), e `build/interaction-label-spacing-final.log`, **8/8 casos aprovados** (26,472 s). O filtro `LabelSpacingTest#homeYouPanel` não selecionou o método parametrizado; por isso a classe inteira foi executada separadamente. Essas rodadas corrigem e aprovam os seis casos que falharam na execução completa; não devem ser descritas como uma única nova execução completa verde.

Build do APK de testes e lint após todos os ajustes de fixture/rolagem: `build/interaction-old-gates-final.log`, `BUILD SUCCESSFUL`, 52 s. As correções desta seção são nos testes: nenhuma exigência visual foi removida, e o layout de produção do cadastro não foi alterado.

A reexecução inicial ficou presa ao encerrar `InstrumentationActivityInvoker.EmptyActivity`. Depois, até `adb shell echo alive` e o console local deixaram de responder. Foi confirmado o processo do único `Pixel_8` e realizado boot frio com `-no-snapshot-load -no-snapshot-save`, sem `-wipe-data`. ADB retornou, `sys.boot_completed=1` e os quatro pacotes principal/testes/validação permaneceram instalados. Os dois casos de Picker executaram em 28,433 s: opacidade aprovada e Cinema reproduzido com captura conclusiva. Isso foi recuperação do ambiente; a aprovação dos casos corrigidos vem das reexecuções finais documentadas acima.

A revisão automática rejeitou `adb root` por alterar a segurança do emulador sem autorização específica. A ação não foi executada. O diagnóstico continuou por logs/capturas acessíveis e recuperação normal do AVD.

## Validação final do balão e regressões

A rodada em `build/interaction-final-28-device.log` aprovou **28/28 casos** (354,013 s): quatro de fala ao vivo, nove de gestos, três de continuidade e 12 da matriz visual. Os quatro casos de fala usam o mesmo `rememberInteractiveSpeech` da Home de produção, com fonte 100%/130% e fala automática presente/ausente. Validam início efetivo, prioridade, resposta silenciosa sem reiniciar prazo, expiração aos 3,5 segundos, retorno ao texto automático atualizado e posição invariável da cena, atividade e contexto.

Build debug, APK AndroidTest e lint após a extração desse controlador passaram em `build/interaction-speech-gates-retry.log` (8m16s). A primeira tentativa foi interrompida por renomeação bloqueada no cache Gradle; a reexecução passou sem apagar o cache.

## APK ativado e Home principal

Após os gates de interface, a flag foi ativada. Build debug, APK AndroidTest, testes selecionados e lint da configuração final passaram em `build/interaction-enabled-final-gates.log` (`BUILD SUCCESSFUL`, 19m26s). A checagem unitária da configuração ligada aprovou **28 casos, zero falhas, zero erros e zero ignorados**, incluindo ViewModel/Room com flags ligada e desligada. A suíte completa de 1.195 casos anterior à alteração da constante foi preservada em `build/interaction-final-1195-jvm-results`.

O APK `build/interaction-validation/app/outputs/apk/debug/app-debug.apk` foi instalado com `adb install -r` no pacote principal, sem limpar dados. A inicialização retornou `Status: ok`; `MainActivity` ficou em foreground sem erro AndroidRuntime. A captura `build/interaction-main-enabled.png` registra a Home. A pressão no personagem gerou “Carinho assim é bom demais.” em `build/interaction-main-hold-live.png`, preservando postura, posição e controle de videogame. `build/interaction-main-resumed.png` registra a retomada com diálogo automático. As capturas foram revisadas.

## Gates de validação

- [x] Testes unitários de interações e continuidade após os últimos ajustes.
- [x] Regressões de animação e suíte unitária completa.
- [x] Build debug e lint debug (fontes principais finais).
- [x] Compilação e execução dos testes instrumentados de gestos e matriz.
- [x] Compilação e execução do novo teste instrumentado de continuidade ao vivo.
- [x] Suíte instrumentada anterior: seis falhas diagnosticadas, corrigidas e aprovadas por reexecução dos casos/classes afetados.
- [x] Matriz visual: 360×640, 360×800, 411×891 dp; fontes 100% e 130%.
- [x] Postura em pé, sentada e sentada de frente; Escritório, Restaurante e Loja com objetos.
- [x] Transporte em movimento e sono (política), troca de cena e saída/retorno da Home (sessão/ciclo de vida).
- [x] Ações nomeadas de toque e carinho com serviço TalkBack ativo no Android.
- [x] Ações equivalentes de acessibilidade verificadas pelo framework Android; navegação humana não realizada. Homologação física dispensada pelo usuário.
- [x] Verificação em aparelho físico dispensada explicitamente pelo usuário em 14/10/2026.
- [x] Ausência de escrita no Diário, contexto, mobilidade e necessidades (ViewModel + Room reais).

## Auditoria por requisito do plano

| Requisito | Evidência atual | Limite ou pendência |
| --- | --- | --- |
| Modelos, política, catálogo e seleção determinística | Pacote `engine/interaction`, 110 regras/108 frases; testes de catálogo, resolver, histórico e política; prioridade da mesa corrigida e regressão aprovada | Sem pendência de implementação identificada nesse requisito |
| Toque apenas nos pixels visíveis, margens e escala inteira | Hit test e máscara de autoria; testes de transparência, oclusão por objeto e gestos Android | Testes físicos dispensados pelo usuário |
| Toque longo único, arraste e troca de atividade | Nove testes de gestos aprovados, incluindo nova pressão exigida após exercício; ação nomeada aprovada com TalkBack ativo | Navegação gestual humana não realizada; ações verificadas no framework |
| PET_HEAD/PURR/NUZZLE e posturas | Clips 600/1200/700 ms, 169 IDs registrados; posturas em pé, sentada e sentada de frente verificadas; folha visual revisada; três cenários Android ao vivo aprovados | Homologação física dispensada |
| Esperar limite seguro e retomar sem repetir eventos | `HoodieInteractionResumeTest`, `HoodiePettingIntegrationTest`, `HoodieInteractiveReactionLifecycleTest`; continuidade Android ao vivo aprovada | Homologação física de objetos dispensada |
| Cancelar pendência na troca de cena/contexto/Home | ReactionResolver com cancelamento específico; sessão por ciclo de vida; testes de pendência, expiração e reentrada | Testes JVM aprovados; cenário de pressão mudando de cena aprovado no Android |
| Segurança de sono e transporte | Política com bloqueio por dúvida, movimento, condução, sono; observação de parada fresca na ViewModel | Não apresentar horário de rotina como prova de parada |
| Sequência única, sem fila e prioridade da fala | Reserva idempotente, um `HomeUiEvent.InteractionResponse`, balão prioritário por 3,5 s; histórico apenas na sessão | Quatro combinações aprovadas no Android: fonte 100%/130%, com/sem fala automática; tempo e posição dos cartões preservados |
| Sem escrita ou alterações nas necessidades/Diário | Snapshot de todas as tabelas Room + InvalidationTracker na ViewModel real, flag ligada/desligada | Dois casos passaram com zero escritas/invalidações |
| Compatibilidade desligada e parâmetros opcionais | Flag ativada após homologação; testes explícitos ligados/desligados preservam o caminho anterior | Flag true; pode ser desabilitada em HoodieConfig |
| Build, lint, JVM e Android | 1.195 JVM sem falhas, build/lint finais aprovados; 24 Android novos aprovados na rodada completa; ação com TalkBack ativo aprovada; seis falhas antigas aprovadas nas reexecuções | 28 casos Android finais aprovados, incluindo aparecimento/desaparecimento do balão |
| Matriz visual | 12 combinações e 24 capturas revisadas, incluindo fonte 130% | Layout fixo não prova toda a animação ao vivo |
| Falhas antigas de CI | Rodada completa preservada e diagnóstico individual; quatro casos + classe de espaçamento (8 casos) aprovados nas reexecuções isoladas | Não apresentar a rodada completa original como verde |
| Emulador, aparelho físico e TalkBack | Pixel 8 emulado; serviço TalkBack ativo com ação nomeada aprovada | Testes físicos dispensados pelo usuário em 14/10/2026; navegação gestual humana ainda não comprovada |

## Comandos de validação

O diretório de build habitual apresentou bloqueio de `R.jar` e cache KSP inconsistente. Nenhum processo externo foi encerrado e nenhum diretório existente foi apagado. A validação usa `build/interaction-validation` e cache de projeto `build/interaction-gradle-cache`, com o mesmo cache Gradle local.

Init script local usado em `build/interaction-validation.init.gradle`:

```groovy
gradle.beforeProject { project ->
    project.layout.buildDirectory.set(new File(project.rootDir, "build/interaction-validation/${project.name}"))
}
```

Comando (PowerShell; JDK 21 configurado):

```powershell
.\gradlew.bat --offline --gradle-user-home .gradle-user-home --project-cache-dir build\interaction-gradle-cache --init-script build\interaction-validation.init.gradle '-Pksp.incremental=false' :app:testDebugUnitTest --tests '*engine.interaction.*' --tests '*HoodiePettingIntegrationTest' --tests '*HoodieInteractionResumeTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --continue --console=plain
```

Logs locais: `build/interaction-isolated-tests.log` (primeira execução) e `build/interaction-gates.log` (revalidação). Relatórios: `build/interaction-validation/app/reports`.






