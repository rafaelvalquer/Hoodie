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
- B4: pendente auditar ambiguidade, segunda visita, ENTER/DWELL repetidos e
  atualização da última visita, preservando cadastro sem geofence ativa.
  A última visita agora é independente da criação: cadastro manual/remoto não
  registra uma visita; respostas tardias usam o início do intervalo original.
  `markVisited` preserva a maior data recebida. A resolução de ENTER/DWELL no
  receiver considera os candidatos em conjunto e a precisão da posição pontual;
  evidência ambígua mantém o lugar atual ou adia a escolha. A posição não é
  adicionada ao histórico de geofences. Uma checagem pontual também evita escolher
  arbitrariamente entre estabelecimentos próximos.
- B5: classificação e vínculo tardios mantêm o ID e os limites da visita original,
  incluindo respostas depois da saída. O contexto atual e correções explícitas
  são preservados. Pendente validar edição/exclusão e demais integrações.
- B6: regressões cobrem consulta por proximidade sem mesclar vizinhos,
  persistência de favoritos, filtros, ordenação e segunda visita ENTER/DWELL.
  `NewPlaceSaveIntegrationTest` cobre concorrência entre facades, falha de banco
  com rollback/nova tentativa, somente hoje, reutilização, posição manual e
  vínculo histórico. `NewPlaceSaveSheetTest` cobre controles Compose, fontes
  ampliadas e ausência de prompts durante deslocamento em veículo. Estes dois
  testes novos ainda precisam ter sua execução aprovada.

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
- C5: revisar imagens nos quatro períodos antes de substituir goldens. Pendente
  `StoreSceneDeviceTest` pela navegação real.
- C6: medir FPS, P95 e memória no emulador. Android físico indisponível, conforme
  resposta do usuário; homologação física permanece pendente.

## Gates finais

Ainda não comprovados para o conjunto atual de alterações:

- `:app:testDebugUnitTest`: zero falhas, com classificação das nove falhas citadas.
- `:app:lintDebug` e `:app:assembleDebug`, incluindo sprites no APK.
- `:app:connectedDebugAndroidTest`, incluindo migrações e navegação da Loja.
- Cenário Casa → Carro → Cantina da Praça → Loja → Casa, com retorno à Cantina
  em outro dia sem nova pergunta quando a identificação for inequívoca.
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
Pixel 8 AVD. Resultado da suíte instrumentada completa ainda pendente; os
resultados das suítes direcionadas não a substituem.

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
cifrado. Recriação da Activity não comprova reinício completo do processo. O
fluxo de descoberta da Loja por pergunta ainda requer sua própria validação.
