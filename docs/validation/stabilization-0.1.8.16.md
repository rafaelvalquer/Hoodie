# Estabilização 0.1.8.16 — acompanhamento

Plano de referência: “Sprint de estabilização 0.1.8.16 — Mobilidade, Lugares
Inteligentes e Loja com NPCs”, fornecido pelo usuário. Este documento acompanha
o escopo completo; resultados antigos não substituem validação da versão atual.

## Mobilidade — A1 a A5

- A1: `TransportFeatureBuilder` separa início de sessão, movimentos e restauração
  de agregados. Validar preservação de veículo, paradas, idempotência e reinício.
- A2: DAO/repositório têm atualizações direcionadas e transações para trechos,
  evidências e reservas. Validar concorrência, sessão encerrada e resultado antigo.
- A3: a engine resolve caminhada pendente em eventos, `onCheck()` e entrada na
  Home. Nova regressão cobre reinício, abertura repetida e ausência de outro ENTER.
- A4: o sampler usa Worker e reservas persistidas; localização de chegada ocorre
  fora do mutex. Validar permissões, cancelamento, orçamento e throttle.
  A reserva agora aplica cinco tentativas e intervalo mínimo de 60 s ao
  deslocamento inteiro, mantendo agregados independentes por período de veículo.
  Contadores legados da sessão e dos períodos são reconciliados sem duplicação.
  A gravação dos dois contadores ocorre na mesma transação Room.
- A5: executar as suítes atuais, classificar cada falha e conferir o percurso
  Casa → Caminhada → Carro → Semáforo → Carro → Estacionamento → Caminhada → Trabalho,
  incluindo Diário, decisões, cenas, reinício e leituras pontuais.

## Lugares — B1 a B6

- B1: `NewPlaceSaveSheet` oferece nome opcional, candidatos, reutilizar,
  somente hoje e coordenadas manuais. A Home oculta descoberta/salvamento com
  evidência de veículo em movimento. Ações de salvamento sinalizam ocupação.
- B2: consulta `findNearby` implementada com validação de coordenadas/raio,
  ordenação por distância e sem fusão automática. Os candidatos são oferecidos na
  conversa e ordenados por compatibilidade de tipo. Busca inicial: 75 m,
  independente do raio de geofence. Sem metadados de precisão na observação
  legada, a interface informa que a posição é aproximada e exige escolha explícita.
  Lugar, vínculo histórico e resposta são gravados na mesma transação Room;
  uma resposta persistida impede repetição, inclusive por outra facade.
- B3: lista ganhou categorias, busca sem distinção de acentos/maiúsculas,
  ordenação, favoritos, última visita e estado de monitoramento. Atualização de
  favorito altera somente esse campo. “Registros de chegada” descreve o contador
  existente; não representa confirmações explícitas nem visitas deduplicadas.
  A edição de nome, categoria, raio e posição agora usa UPDATE direcionado;
  não sobrescreve favorito, contagem e data de uma chegada concorrente.
- B4: pendente auditar ambiguidade, segunda visita, ENTER/DWELL repetidos e
  atualização da última visita, preservando cadastro sem geofence ativa.
  A última visita agora é independente da criação: cadastro manual/remoto não
  registra uma visita; respostas tardias usam o início do intervalo original.
  `markVisited` preserva a maior data recebida. A resolução de ENTER/DWELL no
  receiver considera os candidatos em conjunto e a precisão da posição pontual;
  evidência ambígua mantém o lugar atual ou adia a escolha. A checagem pontual
  também recebe o placeId atual para evitar nova descoberta entre vizinhos.
  A posição não é
  adicionada ao histórico de geofences. Uma checagem pontual também evita escolher
  arbitrariamente entre estabelecimentos próximos.
- B5: classificação e vínculo tardios mantêm o ID e os limites da visita original,
  incluindo respostas depois da saída. O contexto atual e correções explícitas
  são preservados. Uma regressão de renomeação/exclusão verifica identidade,
  intervalo, origem e confiança da visita. Ao excluir uma Loja, a categoria
  histórica vem de `venueType`; não passa silenciosamente para Mercado.
- B6: regressões cobrem consulta por proximidade sem mesclar vizinhos,
  persistência de favoritos, filtros, ordenação e segunda visita ENTER/DWELL.
  `NewPlaceSaveIntegrationTest` cobre concorrência entre facades, falha de banco
  com rollback/nova tentativa, somente hoje, reutilização, posição manual e
  vínculo histórico. `NewPlaceSaveSheetTest` cobre controles Compose, fontes
  ampliadas e ausência de prompts durante deslocamento em veículo. As duas
  suítes passaram nas execuções registradas abaixo.

## Loja — C1 a C6

- C1: validar Loja por ajuste manual, estabelecimento salvo e novo cadastro,
  preservando Mercado, Restaurante e Trabalho.
- C2: revisar um ciclo completo do cachorro, caminho, direção, falas, ações,
  continuidade entre recomposições e entrada/saída fora da área visível.
- C3: validar roupa, provador, pagamento, sacola e porta; nenhum objeto deve ficar
  ativo depois da interação.
- C4: validar mudanças de contexto/planta/variante, retorno do app e caches
  durante vários ciclos. O cache de ciclos compilados de compras agora usa LRU
  com limite de 32 ciclos. Uma regressão percorre 24 horas de Loja e Mercado e
  compara estado, movimento e objetos ao voltar para ciclos já removidos.
  A mudança de período conserva a história e o relógio do comprador; dia,
  variante, estabelecimento ou planta diferentes iniciam outra sessão.
  A iluminação continua acompanhando o período atual. Validação nova abaixo.
- C5: revisar imagens nos quatro períodos antes de substituir goldens.
  `StoreSceneDeviceTest` passou para ajuste manual, cadastro salvo, navegação
  entre abas, recriação da Activity e descoberta pela interface.
- C6: medir FPS, P95 e memória no emulador. Android físico indisponível, conforme
  resposta do usuário; homologação física permanece pendente.

## Gates finais

Resultados atuais e gates ainda pendentes:

- `:app:testDebugUnitTest`: 993 casos, zero falhas e seis ignorados antes do
  cenário integrado final. As nove falhas originais foram classificadas com
  logs e HTMLs do CI em `stabilization-ci-original-failures.md`.
- `:app:lintDebug` e `:app:assembleDebug`: aprovados, zero erros/79 avisos de lint;
  17 arquivos de sprites confirmados no APK.
- `:app:connectedDebugAndroidTest`: suíte completa final ainda pendente,
  incluindo migrações; resultados direcionados de Loja registrados abaixo.
- Cenário Casa → Carro → Cantina da Praça → Loja → Casa, com retorno à Cantina
  em outro dia sem nova pergunta: teste integrado passou; suíte completa em curso.
- CI remoto aprovado e homologação em uso real.

Não publicar nem declarar a versão homologada com esses itens pendentes.

## Execução local de 12/10/2026

A compilação Kotlin das alterações de mobilidade e lista de lugares passou na
saída anterior. A transformação ASM falhou por ausência de
`DatabaseKeyStore.class` naquele diretório intermediário; os testes não chegaram
a executar. Isso não constitui aprovação nem falha funcional dos testes.

A validação foi iniciada novamente com saída exclusiva em
`.gradle-workspace/stabilization-20261012`, pelo comando:

```powershell
.\gradlew.bat -I .gradle-workspace/stabilization-20261012.init.gradle :app:testDebugUnitTest --tests 'com.hoodie.app.engine.mobility.*' --tests 'com.hoodie.app.integration.mobility.*' --tests 'com.hoodie.app.presentation.PlaceListQueryTest' --tests 'com.hoodie.app.integration.PlaceRepositoryNearbyTest' --no-parallel --max-workers=1
```

Essa execução terminou com **71 testes aprovados, zero falhas e zero ignorados**,
em 14 classes, antes das mudanças no novo salvamento de lugares. XMLs conferidos
em `.gradle-workspace/stabilization-20261012/test-results/testDebugUnitTest`.
A reconciliação é acionada a cada `ON_START` da Home, inclusive quando o
ViewModel foi preservado no retorno do background.

Uma execução subsequente foi iniciada para `NewPlaceSaveIntegrationTest`,
`ContextEngineIntegrationTest`, `PlaceRepositoryNearbyTest` e `PlaceListQueryTest`.
Seu resultado está pendente. A expectativa antiga que iniciava a visita apenas
na hora da resposta foi alterada para preservar seu começo original, conforme B5;
a regressão também verifica que o ID do evento permanece o mesmo.

A compilação intermediária dessa execução detectou que o teste novo de cache
estava adiantado em relação às classes de produção já compiladas. Foi iniciada
uma nova validação do conjunto atualizado, incluindo `ShoppingNpcBrainTest`,
regressões de mobilidade e `compileDebugAndroidTestKotlin`. O filtro `--tests`
pertence ao task `testDebugUnitTest` e deve vir antes do próximo task Gradle.

Essa validação subsequente terminou com **75 testes aprovados, zero falhas e zero
ignorados**, em 13 classes. Incluiu os sete testes de `NewPlaceSaveIntegrationTest`,
as 14 regressões de `ContextEngineIntegrationTest` e o cache de 24 horas de
`ShoppingNpcBrainTest`. `compileDebugAndroidTestKotlin` também passou. Esses
resultados antecedem os ajustes de datas e resolução de geofences sobrepostas.

Em seguida foi executada a suíte unitária completa: **934 testes, 927 aprovados,
uma falha e seis ignorados**. A falha é `NewPlaceScenesTest`, no golden
`shopping_look`: SHA-256 esperado
`a1b1c342793b66a26bb3cd68608cd2fcdba89ca88b8d40c317c456a529a4d0c6`, atual
`9eba9a94bd2bd217807f65087cab469a7160a4c35be1d2f14a5abb14b4e4f982`.
Classificação: **referência visual que precisa ser revisada**. A folha atual foi
gerada em `app/build/pixel-preview/new_place_scenes.png`. Em **13/10/2026**, o
usuário respondeu “Aprovar a aparência mostrada”. Foram atualizados somente os
quatro hashes `shopping_*` dessa folha; Escola, Família e Passeio permaneceram
iguais. Essa aprovação não abrange o manifesto completo de NPCs v3.
`NpcArtV3GoldenTest` passou nesta execução.
Os demais testes citados em A5 não falharam nesta execução; isso não substitui
a análise de causa individual dos nove resultados do CI antigo.

A falha unitária interrompeu o comando antes dos testes de dispositivo. Na
execução separada no Pixel 8 AVD, **20 de 25 testes passaram**:
`DiaryControlsTest` (7/7), `DayPresentationUiTest` (7/7), `JourneyMapUiTest` (5/5)
e a proteção contra prompts ao dirigir em `NewPlaceSaveSheetTest` (1/6).
Os outros cinco falharam porque os seletores buscavam “Salvar” com diferenciação
de maiúsculas; `PixelButton` mostra “SALVAR”. Classificação: **defeito no teste**.
Seletores corrigidos para ignorar maiúsculas, preservando as verificações de
ação, dados e estado habilitado. Nova execução iniciada, incluindo o novo
`StoreSceneDeviceTest`, lint e assemble. `lintDebug` e `assembleDebug` passaram:
relatório de lint com zero erros e 79 avisos. A compilação inicial do teste de
navegação detectou o nome incorreto de um DAO e um helper de scroll restrito a
`ComposeContentTestRule`. Ambos foram corrigidos; o helper agora aceita também
`createEmptyComposeRule`, usado para abrir a `MainActivity` real. Nova execução
dos testes de ficha e navegação iniciada; resultado pendente.

Nessa execução, os **seis testes de `NewPlaceSaveSheetTest` passaram**, incluindo
salvamento, reutilização, somente hoje, posição manual, fonte ampliada e proteção
durante deslocamento. Os dois testes de navegação da Loja falharam ao procurar
controles antes da conclusão do carregamento assíncrono. O teste de cadastro
salvo já verificou cena, placeId, cifragem e recriação antes de falhar na lista.
Foram adicionadas esperas pela existência dos controles e do item da lista,
sem remover as asserções. Reexecução de `StoreSceneDeviceTest` pendente.

Após ajustar as esperas da navegação e do layout após recriação, **os dois testes
de `StoreSceneDeviceTest` passaram**. O helper de scroll verificava uma margem
de 32 dp em torno do botão final, que não era atingível no fim da ficha. Agora
verifica visibilidade completa e centro do toque na área útil com margem de
16 dp. A execução conjunta detectou uma expectativa antiga em
`PlaceTypeSelectorIntegrationTest`: procurava o seletor removido da Home.
O teste foi migrado para “Mais ações → Ajustar atividade → Restaurante → Salvar
correção”, preservando as verificações de contexto, cena e fechamento.
Nova suíte unitária completa e regressões dos consumidores do helper em curso.

A nova suíte unitária completa, após a aprovação visual e a correção final de
ambiguidade, terminou com **934 testes, zero falhas, zero erros e seis ignorados**
(928 aprovados). Totais conferidos em todos os XMLs do diretório de resultados.
A execução instrumentada conjunta inclui `StoreSceneDeviceTest`,
`PlaceTypeSelectorIntegrationTest` e `PixelLabNpcTest`: **nove testes aprovados,
zero falhas e zero ignorados**, com `BUILD SUCCESSFUL`. Isso valida também os
demais consumidores do helper de scroll alterado.

Em seguida foi iniciada `:app:connectedDebugAndroidTest` sem filtro de classes,
no mesmo diretório de build exclusivo. O runner anunciou **324 testes** no
Pixel 8 AVD. A execução terminou com **BUILD SUCCESSFUL** em 24m11s. O XML tem
**324 casos: 144 aprovados, 180 ignorados, zero falhas e zero erros**. Os 180
ignorados pertencem a `ScreenGoldenMatrixTest`, cujo gate visual depende das
referências habilitadas; não representam comparações de imagem aprovadas.
O contador de progresso do runner terminou em 504 por contar esses skips
novamente; o total acima vem dos elementos `testcase` e atributos do XML.

### Integração atual de 13/10/2026

O checkout foi atualizado externamente para `48bcf7fb`, integrando também Passeio
2.0, Home Agora e PixelBuffer.rect. Os resultados anteriores não homologam
automaticamente essa integração. Foi iniciada nova execução de unitários,
`StoreSceneDeviceTest`, lint e assemble sobre essa versão. O terceiro cenário da
Loja simula uma pergunta de descoberta com coordenadas cifradas e responde pela
Home: escolher Loja, dar nome, salvar, conferir placeId, cifragem e recriar a
Activity. O GPS e o orçamento diário de perguntas são isolados na preparação
do teste; o caminho de resposta e renderização é o de produção.

Na integração `48bcf7fb`, os unitários executaram **984 testes: 976 aprovados,
duas falhas e seis ignorados**. As falhas são `NewPlaceScenesTest` (`leisure_walk`)
e `NpcArtV3GoldenTest` (`scene_leisure`), devido ao Passeio 2.0 integrado. Os
quatro hashes do Mercado continuam iguais à aparência aprovada pelo usuário.
Classificação das duas falhas: referências visuais alteradas pela integração,
pendentes de revisão naquela execução. A aprovação posterior está registrada
abaixo. Não atualizar o manifesto de NPCs v3 sem aprovação humana
explícita dos itens exigidos pelo gate. A folha atual de cenas está em
`app/build/pixel-preview/new_place_scenes.png`.

O APK gerado contém **17 arquivos em `assets/pixel/hoodie/`**, incluindo sprites,
metadados e âncoras. Isso verifica empacotamento, não aparência ou FPS.

Novas regressões unitárias aprovadas antes do ajuste final de ambiguidade:
`PlaceArrivalPolicyTest`, `OverlappingPlacesIntegrationTest` e atualização de datas
em `PlaceRepositoryNearbyTest`/`NewPlaceSaveIntegrationTest`.

O ajuste final de ambiguidade também passou nos três testes de
`OverlappingPlacesIntegrationTest`: manter o contexto atual diante de um ENTER
do vizinho, sem posição, não entrega uma chegada inventada ao MobilityEngine.

`StoreSceneDeviceTest` usa uma entry point disponível somente em debug para
acessar o grafo Hilt real, sem abrir um segundo banco. Cobre ajuste manual por
controles da Home, navegação Lugares → Home, recriação da Activity, distinção de
Loja/Mercado/Restaurante/Trabalho, cadastro salvo, placeId e payload de coordenadas
cifrado. Na integração `48bcf7fb`, **os três testes passaram**, incluindo o novo
fluxo de descoberta da Loja por pergunta. Recriação da Activity não comprova
reinício completo do processo.

### Correções e revisão visual após a integração

Lint e assemble da integração passaram: zero erros de lint e 79 avisos. Após os
ajustes finais de ambiguidade pontual e categoria histórica,
`OverlappingPlacesIntegrationTest` (4), `PlaceHistoryIntegrationTest` (1) e
`NpcVisualReviewExportTest` (3) passaram: **oito testes, zero falhas**.

O exportador de revisão foi atualizado para consultar `scene.ambientNpcs`,
respeitando os passageiros atuais do transporte e a variante que contém o pato.
Não rotula mais um frame de ônibus como cabeça caindo de um pato inexistente.
A galeria agora inclui Loja nos quatro períodos. A matriz candidata está em
`.gradle-workspace/stabilization-20261013-review/index.html`: 203 relatórios de
qualidade, nenhum com falha HARD. Isso é evidência técnica, não aprovação visual.
O painel local `http://127.0.0.1:8765/` foi conferido com HTTP 200 e aberto para
revisão; aprovação humana da matriz completa e do Passeio recebida conforme
registro abaixo.
O helper local foi encerrado após a aprovação. Os manifestos fonte e de
`docs/npc-art-review/v3` agora registram a aprovação humana explícita recebida
em 13/10/2026: “Aprovar a matriz e as cenas”. A galeria revisada foi preservada
nesse diretório de documentação. O gate `approveNpcV3Goldens=true` passou e
atualizou somente `scene_leisure` no arquivo de hashes NPC v3; os três hashes
`leisure_*` também foram atualizados conforme as imagens aprovadas.

### Edição concorrente e desempenho da Loja

A edição direcionada passou em 49 testes de repositório, seletor, Diário e
animações. A regressão intercala uma chegada e um favorito durante a cifragem
da edição, verificando que nome/raio mudam e os demais dados são preservados.

Os dois testes novos de desempenho passaram no Pixel 8 AVD. A primeira janela
após aquecimento mediu **30,04 FPS, render P95 de 5,62 ms e bitmap P95 de 0,42 ms**.
Em 30 minutos virtuais, o heap Java retido variou de 35,67 a 36,15 MB; sprites
permaneceram em 96/96 entradas. Limites e valores detalhados estão em
`docs/performance/store-performance.md`. Isso não homologa Android físico.
O contador do custo específico do comprador foi corrigido após a primeira
medição; nova validação completa, em modo de comparação de goldens, está em curso.

Essa validação unitária terminou com **987 testes, zero falhas e seis ignorados**.
Os cinco testes instrumentados direcionados também passaram: três de navegação
da Loja e dois de desempenho. A medição final registrou **30,08 FPS, render P95
de 8,08 ms, comprador P95 de 5,41 ms e bitmap P95 de 0,24 ms**. Os sete pontos de
heap Java retido ficaram entre 35,69 e 36,16 MB. Lint/assemble ainda estavam em
execução no momento desse registro; não declarar o gate completo antes do término.

A execução terminou com **BUILD SUCCESSFUL** em 11m26s: unitários completos,
cinco testes instrumentados direcionados, lint e assemble aprovados para esse
conjunto. A suíte instrumentada completa anterior não substitui uma nova execução
final após as correções restantes. Permanecem pendentes o orçamento de leituras
por deslocamento, os cenários integrados do plano, CI remoto e uso real/físico.

### Orçamento de GPS por deslocamento — 13/10/2026

Corrigido o limite que antes era renovado a cada período de veículo. Agora
`reserveEvidenceSample` reconcilia a contagem persistida da sessão com a soma
dos períodos, aplica o intervalo de 60 s e reserva no máximo cinco tentativas
em uma transação. Leituras rejeitadas ou canceladas também consomem a tentativa;
a reserva pode ser liberada, mas o orçamento não é devolvido. O agendador não
cria nova leitura quando o orçamento já terminou. Não foi adicionado GPS contínuo.

A execução direcionada terminou com **68 testes, zero falhas e zero ignorados**.
Inclui troca veículo/caminhada/veículo e reinício mantendo o intervalo, recuperação
dos contadores da versão anterior, duas facades disputando a quinta tentativa,
cancelamento e agregados independentes dos períodos. XMLs no diretório isolado
de validação. Essa execução antecede o novo teste integrado do percurso de carro
e o ajuste de continuidade do comprador na mudança de período.

### Percurso integrado e continuidade de compras

Passaram os 11 testes de `MobilityEngineIntegrationTest` e
`ShoppingSessionContinuityTest`. O novo percurso configura uma preferência de
carro antes de sair de casa e usa Room, engines, sampler e montador de Diário
reais, com sensores e relógio controlados. Percorre caminhada inicial, carro,
semáforo, retomada, estacionamento e caminhada até o Trabalho. Confere cenas,
fonte PREFERENCE do carro, confiança, limites contínuos dos segmentos e uma
única leitura pontual. Reinicia a engine com caminhada pendente e confirma a
troca após 45 s, sem novo ENTER nem segmentos duplicados ao reabrir a Home.
Isso simula recuperação de estado no banco, não um force-stop Android.

A primeira preparação desse novo teste pressupunha que evidência insuficiente
sempre produziria uma pergunta de transporte; essa suposição não vale na
inteligência atual. O cenário final usa a preferência explícita de carro e
mantém as verificações do percurso. Os testes existentes de transporte
desconhecido, confirmação e correção continuam separados.

A Loja agora mantém o comprador e sua origem temporal ao mudar o período ou
retornar após uma pausa. O fundo e a iluminação usam o período atual; a história
do comprador conserva o período de entrada até mudar a sessão. Dia, variante,
categoria/planta e saída do contexto criam sessões independentes. A origem
temporal nula substitui o sentinela zero, permitindo iniciar a reprodução em
0 ms. Dois testes verificam identidade, relógio, retomada e descarte.

O agendamento também foi ligado ao orçamento global: ao esgotar cinco
tentativas, não agenda novas checagens de velocidade em outro período da mesma
viagem. A suíte unitária completa, lint e assemble estão sendo reexecutados
após esse ajuste adicional.

A reexecução unitária completa passou: **993 testes, zero falhas, zero erros e
seis ignorados**. Inclui nove testes de `VehicleEvidenceTest`, nove de
`MobilityEngineIntegrationTest`, dois de continuidade de compras, o golden NPC v3
e os 20 testes de novas cenas. `approveNpcV3Goldens=false`: referências apenas
comparadas. Lint e assemble ainda estavam em andamento ao registrar os XMLs.

Os gates terminaram com **BUILD SUCCESSFUL** em 9m41s: 993 unitários,
lint com zero erros e 79 avisos, assemble e 17 assets de sprites no APK. Depois,
os **cinco testes instrumentados direcionados passaram** em 2m04s: três de
navegação/cadastro/recriação da Activity e dois de desempenho da Loja. A nova
janela mediu 30,17 FPS, render P95 de 8,16 ms, comprador P95 de 5,92 ms e bitmap
P95 de 0,33 ms. O teste de 30 minutos virtuais levou 24,96 s reais; heap Java
retido entre 35,67 e 36,15 MB e cache de sprites em 96/96 entradas. Valores
preservados em `docs/performance/store-performance.md`.

Nenhum processo Gradle dessa validação ficou pendente. Esses resultados fecham
os ajustes de orçamento e continuidade descritos nesta etapa. Não substituem
a suíte instrumentada completa final, o cenário integrado Casa → Cantina → Loja
→ Casa e segunda visita, a classificação histórica das nove falhas, o CI remoto
ou homologação física/uso real, ainda pendentes conforme os gates acima.

### Dia integrado, diagnóstico histórico e suíte completa — continuação

`StabilizationDayIntegrationTest` passou com o cenário da seção 6: Casa às 08h,
carro, parada sustentada em restaurante desconhecido, pergunta e cadastro
“Cantina da Praça”, Loja conhecida, ciclo completo do comprador e retorno à Casa.
Confere ordem das visitas no Diário/replay, nomes, categoria STORE, fonte e
intervalo original da Cantina, três trechos de carro fechados e histórico estável
depois da visita seguinte. No outro dia, uma leitura pontual reconhece o mesmo
placeId da Cantina; ENTER/DWELL repetidos não geram outra contagem, cadastro ou
pergunta. O teste usa engines/Room/Diário reais e relógio/sensores controlados.
O snapshot antigo de onboarding da fixture é encerrado antes da primeira
observação real de Casa às 08h, para não confundir ausência de dados de vigília
com uma visita matinal registrada.

A suíte unitária completa com esse cenário terminou com **994 casos, zero
falhas e seis ignorados**. A instrumentação completa está executando 329 casos.
Até agora houve uma falha em `PlacePickerScreenTest` no teste de teclado real:
insets da IME permaneceram em zero. A captura de timeout mostra o tutorial
“Try out your stylus” do Gboard sobreposto ao aplicativo, e o log registra
teclado do tipo `accessory`. `show_ime_with_hard_keyboard=1` já estava ativo;
`stylus_handwriting_enabled` estava ausente (padrão Android = 1).
A classificação é **problema de ambiente de execução**, a confirmar por
reexecução com escrita por caneta desativada somente no ambiente de teste.
Não remover a asserção de teclado real/insets nem pular esse teste.

Referência da configuração de escrita por caneta e seu padrão:
[Settings.Secure no AOSP](https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/provider/Settings.java).
A captura diagnóstica foi preservada em
`.gradle-workspace/stabilization-ime-timeout-20261013.png`.

O CI original foi finalmente localizado pelo endpoint de runs, sem o filtro de
eventos pull request: execução #46, `38326110415`, no commit `f4aab7ba`.
O ZIP original foi baixado e seu SHA-256 confere com o digest do GitHub.
As nove asserções foram classificadas individualmente em
`stabilization-ci-original-failures.md`. A main remota mais recente (`83622916`)
tem duas falhas adicionais nos testes do Passeio 1.0 consultando o plano 2.0;
a adequação desses contratos será feita depois da execução local, preservando
os testes do fallback legado e as regressões próprias do Passeio 2.0.

A execução completa no AVD terminou com **329 casos: 148 aprovados, uma falha,
180 ignorados e zero erros**. A única falha foi a interferência do tutorial do
Gboard descrita acima. Depois de `stylus_handwriting_enabled=0`, o mesmo teste
de teclado real passou (um caso, zero falhas/ignorados), sem alteração de fonte
nem de asserções. O workflow agora aplica essa configuração somente ao
emulador de CI, junto da configuração já existente de teclado virtual.
Os outputs originais do Gradle foram substituídos na reexecução direcionada;
um registro dos valores lidos está em
`.gradle-workspace/stabilization-20261013-full-instrumentation/observed-results.json`.
Não tratar esse registro como uma cópia dos XMLs originais.

A branch `codex/stabilization-0-1-8-16` integra `origin/main` por fast-forward
até `83622916`. Os testes com `NpcPath`/sequência fixa agora consultam
explicitamente `planLegacy`; um novo teste do plano público 2.0 compara estados
determinísticos de sessões independentes e valida o encaminhamento pela cena.
Não foi desligado o Passeio 2.0. A continuidade de compras ganhou saída para
HOME exatamente durante pagamento e caminhada, conferindo descarte de sessão,
reinício do relógio e ausência de caixa ativo herdado. Os nove testes direcionados
passaram. A validação completa dessa integração também passou em **8m14s**:
**1.000 unitários, zero falhas e seis ignorados**, lint e assemble aprovados.

Foi instalado o APK debug novamente para validação pela interface, porque o
Gradle havia desinstalado o app ao terminar a instrumentação. Com localização
negada, o onboarding concluiu e o cadastro manual salvou uma Loja em coordenadas
fictícias. A lista mostrou categoria, zero chegadas, última visita não registrada
e monitoramento indisponível. A categoria Restaurantes ocultou a Loja e Lojas a
restaurou; marcar favorito, editar nome e buscar “EDITADA” preservou o cadastro
e o favorito. A Home abriu a Loja salva pela correção a partir de agora.

O teste externo executou **force-stop real**: PID antes `20677`, nenhum PID após
encerramento, nova abertura COLD com PID `22291`. A Home manteve Compras,
confirmação explícita, nome editado e o horário original 15:28. Capturas e XMLs
antes/depois estão em `.gradle-workspace/hoodie-store-*-restart.*`.
Ainda falta conferir o favorito na lista do novo processo e medir os quadros
do Android; isso não substitui homologação física.

Essas duas verificações foram concluídas: a lista do processo novo manteve nome,
categoria Loja, favorito e indisponibilidade de monitoramento. As capturas
antes/depois mostram a cena própria de roupas. `gfxinfo` da Home real mediu
1.998 frames, P95 de 26 ms e P99 de 36 ms; entretanto, o Android marcou 95,80%
como janky (7,16% na classificação legacy). Isso exige análise adicional e
não comprova ausência de travamentos. Valores e limites em
`docs/performance/store-performance.md`. O cadastro fictício foi excluído pela
interface, conferindo lista vazia; a configuração local de escrita por caneta
foi restaurada ao valor ausente original. A configuração do CI permanece
explícita e restrita ao emulador efêmero do workflow.
