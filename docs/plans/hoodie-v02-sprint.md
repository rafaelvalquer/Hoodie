
# Plano Técnico de Desenvolvimento — Hoodie V0.2
## Sprint: Interações contextuais, coordenação de reações, desempenho e replay

**Repositório:** https://github.com/rafaelvalquer/Hoodie  
**Base técnica:** `main` — `7fe1927cfbc3`  
**Plataforma:** Android / Kotlin / Jetpack Compose  
**Objetivo:** aprimorar a experiência do Hoodie aproveitando os componentes já desenvolvidos, sem alterar indevidamente a rotina real do usuário.

## 1. Diretrizes gerais

Esta sprint terá cinco frentes:

| ID | Funcionalidade | Prioridade | Complexidade |
|---|---|---|---|
| H-04 | Frases e interações nos novos ambientes | P1 | Baixa–média |
| H-05 | Coordenação única de reações | P0 | Alta |
| H-06 | Consistência de contexto e continuidade | P0 | Média |
| H-07 | Desempenho dos NPCs | P0 | Média–alta |
| H-08 | Replay do Dia de Demonstração | P1 | Média–alta |

### Requisitos obrigatórios

- Preservar o funcionamento offline.
- Não modificar dados reais do Diário por causa de interações cosméticas.
- Não reiniciar animações de NPCs sem necessidade.
- Evitar novos serviços de background.
- Manter a compatibilidade com a Home 2.0.
- Reutilizar os motores de animação, cena, diálogo e replay existentes.
- Manter as funcionalidades controladas por feature flags até sua homologação.

---

# 2. H-04 — Frases e interações nos novos ambientes

**Prioridade: P1**

## 2.1. Situação atual

O sistema `HoodieInteractionResolver` já utiliza regras de um catálogo JSON.

Arquivo:

`app/src/main/assets/metadata/interactive_dialogues.json`

O catálogo contém regras específicas para Casa, Escritório, Restaurante, Academia, Passeio, Mercado, Loja, Escola, Família e transportes.

As seis cenas novas ainda não possuem regras próprias de interação:

- `BAKERY`
- `MALL`
- `BEACH`
- `CINEMA`
- `PHARMACY`
- `HOTEL`

Como existe um fallback global, o Hoodie pode responder com uma frase genérica, mesmo estando num estabelecimento que já possui animações e atividades exclusivas.

## 2.2. Solução

Ampliar o catálogo existente, sem criar outro `DialogueEngine`.

Para cada novo ambiente, cadastrar:

1. Duas ou mais frases para toque rápido.
2. Duas ou mais frases para toque longo.
3. Reações específicas para atividades exclusivas.
4. Condições de segurança para carinho.
5. Animações já registradas, evitando novos sprites desnecessários.

A seleção continuará utilizando `scene`, `activity`, `condition`, `priority` e `weight`.

## 2.3. Frases e animações definidas

### Padaria / Cafeteria — `BAKERY`

| Situação | Gesto | Frase | Animação |
|---|---|---|---|
| Olhando a vitrine | Rápido | "Esse cheirinho de pão é irresistível!" | `NOTICE → SMILE` |
| Escolhendo salgado | Rápido | "Qual desses você escolheria?" | `GLANCE → NOD` |
| Esperando o pedido | Rápido | "Será que meu cafezinho já está pronto?" | `LOOK_AROUND → SMILE` |
| Tomando café | Rápido | "Ahhh... agora sim!" | `SMILE → NOD` |
| Sentado, descansando | Longo | "Café e carinho... combinação perfeita!" | `PET_HEAD → PURR` |
| Após comer | Longo | "Agora só falta uma boa soneca." | `PET_HEAD → PURR → NUZZLE` |

**Regra:** não interromper `BAKERY_EAT_PASTRY` ou uma ação de bebida no meio do ciclo.

### Shopping — `MALL`

| Situação | Gesto | Frase | Animação |
|---|---|---|---|
| Olhando vitrines | Rápido | "Será que encontro algo do meu tamanho?" | `NOTICE → HAPPY` |
| Escolhendo loja | Rápido | "Essa loja parece interessante!" | `GLANCE → NOD` |
| Passeando | Rápido | "Tem tanta coisa para conhecer!" | `LOOK_AROUND → SMILE` |
| Descansando | Rápido | "Minhas patinhas precisam de uma pausa." | `STRETCH → SMILE` |
| Parado | Longo | "Até no shopping você lembra de mim!" | `PET_HEAD → PURR` |
| Descansando | Longo | "Agora estou pronto para mais vitrines." | `PET_HEAD → NUZZLE` |

**Regra:** impedir carinho enquanto `MALL_ENTER_STORE` estiver realizando a transição para outro ponto.

### Praia — `BEACH`

| Situação | Gesto | Frase | Animação |
|---|---|---|---|
| Observando o mar | Rápido | "Olha o tamanho dessas ondas!" | `NOTICE → HAPPY` |
| Descansando | Rápido | "Eu poderia ficar aqui o dia inteiro!" | `SMILE → NOD` |
| Tomando sorvete | Rápido | "Espero não derrubar meu sorvete!" | `SURPRISED → SMILE` |
| Passeando | Rápido | "A areia é quentinha nas patinhas!" | `GLANCE → SMILE` |
| Sob o guarda-sol | Longo | "Um carinho com essa brisa é perfeito!" | `PET_HEAD → PURR → NUZZLE` |
| Relaxando | Longo | "Hmmm... que tranquilidade!" | `PET_HEAD → PURR` |

**Regra:** bloquear carinho durante caminhada ou animação de consumo de sorvete. Não acordar o Hoodie durante uma soneca.

### Cinema — `CINEMA`

| Situação | Gesto | Frase | Animação |
|---|---|---|---|
| Aguardando filme | Rápido | "Será que vai ter aventura?" | `NOTICE → SMILE` |
| Assistindo | Rápido | "Shhh... o filme já vai começar!" | `GLANCE` |
| Cena emocionante | Rápido | "Você viu isso?!" | `SURPRISED` |
| Com pipoca | Rápido | "Essa pipoca está ótima!" | `SMILE` |
| Sentado, disponível | Longo | "Um carinho durante o filme é bom demais." | `PET_HEAD → PURR` |
| Após o filme | Longo | "Gostei da nossa sessão!" | `PET_HEAD → NUZZLE` |

**Regra:** durante `CINEMA_EAT_POPCORN`, não remover a pipoca da mão. Evitar reações expansivas enquanto o Hoodie estiver assistindo.

### Farmácia — `PHARMACY`

| Situação | Gesto | Frase | Animação |
|---|---|---|---|
| Olhando prateleiras | Rápido | "Tem bastante coisa por aqui!" | `LOOK_AROUND → SMILE` |
| Examinando produto | Rápido | "Será que é isso que procuramos?" | `GLANCE → NOD` |
| Esperando | Rápido | "Já podemos ir?" | `NOTICE → SMILE` |
| Parado | Longo | "Até aqui você me faz carinho!" | `PET_HEAD → PURR` |
| Disponível | Longo | "Gostei dessa pausa!" | `PET_HEAD → SMILE` |

**Regra:** manter o produto na mão até o encerramento da microação que o utiliza.

### Hotel — `HOTEL`

| Situação | Gesto | Frase | Animação |
|---|---|---|---|
| Fazendo check-in | Rápido | "Será que nosso quarto já está pronto?" | `GLANCE → SMILE` |
| No lobby | Rápido | "Esse hotel parece confortável!" | `NOTICE → HAPPY` |
| Esperando | Rápido | "Espero que tenha um café da manhã caprichado!" | `SMILE → NOD` |
| Olhando o relógio | Rápido | "Será que vai demorar muito?" | `GLANCE → SHRUG` |
| Sentado no sofá | Longo | "Viagem boa também merece carinho." | `PET_HEAD → PURR → NUZZLE` |
| Descansando | Longo | "Agora sim, podemos relaxar!" | `PET_HEAD → PURR` |

**Regra:** não interromper `HOTEL_CHECK_IN` ou `HOTEL_ELEVATOR` antes do ponto seguro.

## 2.4. Alterações técnicas

Modificar:

- `HoodieInteractionModels.kt`
- `HoodieInteractionResolver.kt`
- `HoodieInteractionCatalog.kt`
- `interactive_dialogues.json`

Acrescentar condições específicas quando necessário, utilizando os IDs reais das animações de cada novo estabelecimento.

Não associar todas as animações da cena a uma condição genérica `ANY`: uma frase sobre sorvete deve ser elegível apenas durante `BEACH_ICE_CREAM`, por exemplo.

### Compatibilidade

As flags `VENUE_BAKERY`, `VENUE_MALL`, `VENUE_BEACH`, `VENUE_CINEMA`, `VENUE_PHARMACY` e `VENUE_HOTEL` estão atualmente desativadas para seleção automática.

O catálogo deve funcionar tanto nas cenas abertas manualmente quanto nas cenas que venham a ser habilitadas automaticamente.

**Critérios de aceite:**

- Todas as seis cenas possuem frases próprias.
- Toque rápido e toque longo possuem variantes distintas.
- Não existem animações inexistentes no catálogo.
- As frases correspondem à ação visual realmente executada.
- O fallback permanece funcional.
- Interações novas não alteram os objetos ou a rotina.

---

# 3. H-05 — Unificar a prioridade de reações

**Prioridade: P0**

## 3.1. Problema arquitetural

Atualmente existem mecanismos parcialmente independentes:

- `ReactionResolver`: agenda reações visuais.
- `HoodieInteractionResolver`: seleciona interações do usuário.
- `HoodieInteractionSession`: reserva a interação.
- `InteractiveSpeechState`: apresenta falas de carinho.
- `PersonalityDialogueScheduler`: organiza falas espontâneas.
- `DialogueArbiter`: escolhe entre falas automáticas.
- `ReactionPriority`: classifica prioridades de personalidade, eventos sociais e animações essenciais.

O risco é duas funcionalidades competirem pelo mesmo personagem ou balão.

Exemplo:

O usuário faz carinho no Hoodie exatamente quando o sistema decide que ele deve cumprimentar o Bulldog.

Resultados indesejados:

- Duas animações tentando controlar o personagem.
- Uma fala aparecendo sem a animação correspondente.
- Uma reação espontânea sendo registrada como exibida mesmo tendo sido descartada.
- Um diálogo substituindo outro antes do tempo previsto.

## 3.2. Solução — `HoodieReactionCoordinator`

Criar:

`engine/reaction/HoodieReactionCoordinator.kt`

Componentes auxiliares:

- `ReactionIntent.kt`
- `ReactionSource.kt`
- `ReactionPresentation.kt`
- `ReactionArbitrationPolicy.kt`
- `ReactionLifecycle.kt`

O coordenador será responsável por arbitrar **as reações que precisam utilizar o Hoodie ou seu balão principal**.

As animações ambientais dos NPCs continuarão funcionando em seus próprios motores. Elas não deverão ser pausadas indiscriminadamente.

## 3.3. Nova hierarquia de prioridades

| Ordem | Categoria | Exemplos |
|---|---|---|
| 1 | `SAFETY_TRANSITION` | Transporte, sono, mudança de cena |
| 2 | `ESSENTIAL_ANIMATION` | Terminar refeição, soltar objeto, levantar peso |
| 3 | `USER_INTERACTION` | Toque rápido e carinho |
| 4 | `SOCIAL_EVENT` | Interação do Hoodie com NPC |
| 5 | `PERSONALITY_REACTION` | Cumprimento espontâneo, curiosidade, pausa |
| 6 | `AMBIENT_SPEECH` | Comentários aleatórios |

**Regra fundamental:** prioridade maior não significa interromper instantaneamente uma animação não interrompível. Significa obter preferência no próximo ponto seguro.

### Exemplo

Hoodie está tomando café no Escritório.

1. O motor inicia a animação de bebida.
2. O usuário faz carinho.
3. O pedido recebe prioridade sobre comentários espontâneos.
4. O motor termina o movimento necessário para não perder a caneca.
5. Se a solicitação ainda estiver válida, inicia o carinho.
6. O balão exibe a frase correspondente.
7. O Hoodie retoma a microação anterior.
8. O diálogo automático volta a ficar elegível.

## 3.4. Contrato único de reação

Criar `ReactionIntent` contendo:

- `id`
- `source`
- `priority`
- `sceneId`
- `contextVersion`
- `requestedAt`
- `expiresAt`
- `animations`
- `dialogue`
- `interruptPolicy`

O fluxo de uma reação será:

`REQUESTED → ACCEPTED → DEFERRED (opcional) → STARTED → FINISHED`

Saídas alternativas:

- `REJECTED`
- `EXPIRED`
- `CANCELLED`

Cada alteração deverá ter motivo técnico identificável.

## 3.5. Garantia de início efetivo

O `AnimationStateMachine` já informa o início de uma interação por meio do `RenderFrame`.

Ampliar essa abordagem às reações espontâneas que utilizam o mesmo personagem.

**Não marcar uma reação como apresentada durante a seleção.**

A confirmação deve ocorrer somente quando o renderer efetivamente apresentar seu início.

Esse ajuste é especialmente importante para `PersonalityDialogueScheduler`, que atualmente registra decisões antes da confirmação visual.

### Nova responsabilidade

O coordenador autoriza a reação.

O renderer confirma a execução.

Somente depois dessa confirmação:

- O histórico de exibição é atualizado.
- O cooldown é registrado.
- A fala é apresentada.
- Uma memória de personalidade pode ser marcada como exibida.

Reações canceladas ou expiradas não entram no histórico de exibição.

## 3.6. Controle do balão

Manter um único `SpeechBubble` na Home.

Criar um modelo unificado de apresentação, com:

- ID da reação.
- Texto.
- Origem.
- Prioridade.
- Início efetivo.
- Duração.
- Contexto associado.

Ao término da fala de carinho, o balão poderá retornar à fala automática atual, caso ela ainda seja válida.

Não restaurar um texto antigo de outro ambiente.

### Tratamento de NPCs

Balões próprios dos NPCs não precisam ser encaminhados ao `SpeechBubble` principal. Entretanto, o coordenador deve poder consultar quando uma conversa importante está ocupando a atenção do Hoodie.

Isso evita o personagem responder ao usuário e iniciar simultaneamente uma interação social incompatível.

## 3.7. Migração gradual

Não substituir todos os mecanismos de uma vez.

Ordem:

1. Criar o coordenador e os modelos.
2. Integrar interações do usuário.
3. Integrar reações de personalidade.
4. Integrar eventos sociais que utilizam o Hoodie.
5. Integrar falas ambientais.
6. Remover arbitragens redundantes depois da aprovação dos testes.

Manter adaptadores temporários para os eventos atuais `HomeUiEvent.React` e `HomeUiEvent.InteractionResponse`.

## 3.8. Testes

Criar:

- `HoodieReactionCoordinatorTest`
- `ReactionPriorityTest`
- `ReactionLifecycleTest`
- `PersonalityPresentationTest`
- `ReactionDialogueIntegrationTest`

**Critérios de aceite:**

- Nunca existem duas reações controlando simultaneamente o Hoodie.
- O carinho tem prioridade sobre comentários espontâneos.
- Animações essenciais continuam protegidas.
- Reação cancelada não aparece no histórico.
- O balão apresenta somente uma fala.
- Um NPC não perde sua rotina porque o usuário tocou no Hoodie.
- O mecanismo mantém compatibilidade quando o toque está desativado.

---

# 4. H-06 — Revisar contexto e continuidade

**Prioridade: P0**

## 4.1. Problema identificado

O `HomeViewModel.build()` determina o cenário usando prioritariamente o contexto atual:

`ctx?.type ?: snap.state.userContext`

Porém, `interactionContext()` utiliza `snapshot.state.userContext` para selecionar as frases.

Após uma correção manual, esses dados podem divergir temporariamente.

### Exemplo

O sistema acredita que o usuário está no Escritório.

O usuário corrige para Restaurante.

A Home passa a apresentar Restaurante, mas o snapshot do Hoodie ainda pode estar sendo reconciliado.

Se o usuário tocar nesse instante, existe risco de selecionar uma frase da atividade anterior.

## 4.2. Solução — contexto de apresentação canônico

Criar:

`presentation/screens/home/PresentedHoodieContext.kt`

Esse modelo representará o contexto utilizado para construir a cena que está realmente visível.

Campos propostos:

| Campo | Responsabilidade |
|---|---|
| `contextEventId` | Evento canônico |
| `contextType` | Casa, Trabalho, Restaurante etc. |
| `placeId` | Estabelecimento exato |
| `venueType` | Tipo do estabelecimento |
| `hoodieActivity` | Atividade atual |
| `sceneId` | Cena selecionada |
| `visualRevision` | Versão da apresentação |
| `isReconciled` | Indica se o estado pode receber interações |

A mesma versão de contexto deverá ser utilizada por:

- `VisualDirector`
- `HomeHeroContent`
- `HoodieInteractionResolver`
- `HoodieReactionCoordinator`

Não criar outra fonte persistente de dados. O novo modelo será derivado das fontes canônicas já observadas pela Home.

## 4.3. Sincronização com o renderer

Uma interação deverá ser permitida somente quando:

1. A Home estiver ativa.
2. A cena apresentada corresponder à cena selecionada.
3. O contexto não estiver em reconciliação.
4. O identificador de versão corresponder ao quadro apresentado.
5. A atividade atual permitir a reação.

O `RenderFrame` já carrega informações sobre `interactionVisual`.

Aproveitar esse contrato e acrescentar uma identidade de apresentação quando necessário.

## 4.4. Cancelamento de reações antigas

Uma solicitação pendente deverá ser cancelada quando ocorrer:

- Alteração do evento de contexto.
- Troca do `placeId`.
- Mudança do `venueType`.
- Troca de cena.
- Mudança relevante de atividade.
- Entrada em transporte.
- Entrada em sono.
- Saída da Home.

### Caso importante: dois lugares do mesmo tipo

Exemplo:

Restaurante A → Restaurante B.

Ambos utilizam `SceneId.RESTAURANT`.

Se a validação verificar apenas o `SceneId`, uma interação antiga pode continuar válida apesar da mudança de estabelecimento.

Por isso, a identidade precisa incluir também o evento e o lugar, não somente a cena.

## 4.5. Continuidade após correção manual

Fluxo proposto:

1. Usuário seleciona um novo local.
2. `ContextEngine` confirma e persiste a correção.
3. A Home recebe o novo contexto.
4. As interações anteriores são invalidadas.
5. `VisualDirector` resolve a cena correspondente.
6. A cena é apresentada pelo renderer.
7. O novo contexto é marcado como reconciliado.
8. As interações voltam a ficar disponíveis.

Não aguardar arbitrariamente vários segundos. Utilizar a identidade do estado e a confirmação de apresentação.

## 4.6. Home office

Testar explicitamente:

- Contexto: `WORK`.
- Cena: `HOME`.
- Atividade: `WORKING`.

Nesse caso, o Hoodie deverá utilizar as regras de Escritório, mesmo aparecendo em casa.

A prioridade deverá ser da atividade de trabalho, sem substituir o ambiente visual.

## 4.7. Testes

Criar:

- `PresentedHoodieContextTest`
- `HomeContextReconciliationTest`
- `HoodieInteractionContextConsistencyTest`
- `InteractionPlaceChangeTest`

Matriz:

| Transição | Resultado esperado |
|---|---|
| Casa → Trabalho | Frases de trabalho |
| Trabalho → Restaurante | Frases de refeição |
| Restaurante A → Restaurante B | Reações antigas canceladas |
| Mercado → Loja | Catálogo e cena atualizados |
| Restaurante → Padaria | Falas específicas da Padaria |
| Casa → Home office | Frases de trabalho na cena Casa |
| Trabalho → Casa → Trabalho | Sem reapresentar fala antiga |
| Correção desfeita | Contexto reconciliado novamente |
| Troca de local durante carinho pendente | Carinho cancelado |
| Voltar à Home após navegar | Nenhuma fala obsoleta |

**Critério de aceite:** a frase exibida deve sempre corresponder ao contexto e à atividade que produziram a cena apresentada, sem introduzir mudanças no Diário.

---

# 5. H-07 — Desempenho dos NPCs e cenas complexas

**Prioridade: P0**

## 5.1. Situação atual

O projeto já possui instrumentos relevantes:

- `ScenePerformanceMonitor`
- `SceneRenderer`
- `OfficeNpcSession`
- `VenueNpcSession`
- `LeisureNpcSession`
- Cache de frames dos personagens
- Planejamento determinístico de movimentos

O `ScenePerformanceMonitor` mede tempos de renderização, planejamento, desenho, iluminação, NPCs, atualização de bitmap e indicadores de memória.

Entretanto, a instrumentação detalhada de `SceneRenderer.render()` está concentrada nas cenas de Escritório e Loja.

Isso dificulta comparar novas cenas como Padaria, Shopping e Hotel.

Outro ponto observado: em `PixelViews.kt`, o renderizador do Escritório é explicitamente executado em `Dispatchers.Default`, enquanto as demais cenas usam outro caminho de execução.

Antes de otimizar os NPCs, precisamos identificar se o maior custo vem do planejamento, da renderização ou do trabalho realizado na thread principal.

## 5.2. Ampliar a instrumentação

Modificar:

`pixel/performance/ScenePerformanceMonitor.kt`

`pixel/renderer/SceneRenderer.kt`

Adicionar métricas por `SceneId`:

- Tempo total de renderização.
- Tempo de planejamento dos NPCs.
- Tempo de atualização do estado.
- Tempo de desenho dos NPCs.
- Tempo de desenho dos objetos.
- Tempo de composição do cenário.
- Tempo de iluminação.
- Tempo para apresentar o bitmap.
- FPS efetivo.
- P50, P95 e P99 dos frames.
- Quantidade de NPCs ativos e visíveis.
- Alocações e atividade do coletor de lixo.
- Criações e reconstruções de sessões.

A coleta detalhada deverá ficar restrita a builds de diagnóstico ou ao Developer Lab.

Não introduzir gravação de logs a cada frame em produção.

## 5.3. Cenas de benchmark

Criar uma matriz de testes com:

| Cena | Cenário de estresse |
|---|---|
| Escritório | Todos os NPCs trabalhando, movimentação e conversa |
| Restaurante | Cliente chegando, pedido e entrega |
| Mercado | Comprador circulando, escolhendo e pagando |
| Loja | Cliente usando arara, espelho e provador |
| Passeio | NPCs caminhando, conversando e usando banco |
| Padaria | Barista preparando pedidos e clientes aguardando |
| Shopping | NPCs circulando entre vitrines e lojas |
| Praia | Pessoas descansando, caminhando e interagindo |
| Cinema | Entrada, compra de pipoca e sessão |
| Farmácia | Clientes e atendente em atividade |
| Hotel | Check-in, lobby e movimentação até elevador |

Cada teste deverá executar:

1. Entrada inicial na cena.
2. Primeiro período de animação, ainda com caches frios.
3. Reprodução contínua após aquecimento.
4. Transições entre microações.
5. Interações simultâneas de NPCs.
6. Saída e retorno à cena.
7. Reprodução no período noturno, incluindo iluminação.
8. Teste com toque no Hoodie habilitado.

## 5.4. Metas de desempenho

O renderer trabalha com um intervalo-alvo próximo de 33 ms, correspondente a aproximadamente 30 FPS.

Estabelecer como metas iniciais de homologação:

| Métrica | Meta proposta |
|---|---|
| FPS após aquecimento | Próximo de 30 FPS |
| FPS mediano | Pelo menos 28 FPS no dispositivo de referência |
| Renderização P95 | Até 25 ms |
| Intervalo entre frames P95 | Até 40 ms |
| Travamentos longos | Nenhum congelamento perceptível durante operação normal |
| Crescimento de memória | Sem crescimento progressivo após ciclos repetidos de cena |
| Reinícios de NPCs | Nenhum sem mudança legítima de sessão |

Esses valores são **metas de desempenho**, não medições já alcançadas.

A avaliação deverá comparar o desempenho antes e depois das alterações, em condições equivalentes. Em dispositivos mais fracos, será necessário estabelecer uma referência específica.

## 5.5. Otimização do SceneRenderer

### A. Reduzir alocações por frame

O renderer utiliza operações de filtragem, mapeamento, concatenação e ordenação para reunir objetos e NPCs na profundidade correta da cena.

Essas operações são candidatas a otimização, especialmente quando muitos objetos precisam ser combinados em cada quadro.

Proposta:

- Reutilizar estruturas de composição quando possível.
- Manter ordenação estável por profundidade.
- Recalcular somente elementos que realmente mudaram.
- Evitar criar listas temporárias de objetos estáticos.
- Reaproveitar buffers e estruturas auxiliares.

**Não alterar a ordem visual de profundidade.** O Hoodie precisa continuar passando corretamente atrás e à frente de mesas, balcões e NPCs.

### B. Avaliar renderização fora da thread principal

Investigar a possibilidade de executar a renderização das cenas complexas em `Dispatchers.Default`, assim como já acontece no Escritório.

Separar rigorosamente:

- Planejamento e desenho do pixel buffer: thread de trabalho.
- Publicação do bitmap e atualização da interface Compose: caminho seguro de apresentação.

Não implementar múltiplas renderizações concorrentes da mesma cena.

Manter o mecanismo de sincronização existente e impedir o acúmulo de frames antigos.

### C. Reutilizar snapshots de NPCs

O `VenueNpcSession.snapshotAt()` já possui cache para consultas ao mesmo instante.

Preservar essa otimização.

Garantir que múltiplas consultas de um mesmo frame reutilizem o mesmo snapshot sempre que possível.

Evitar reconstruir sessões apenas porque a iluminação, um balão ou uma propriedade visual mudou.

### D. Planejamento de NPCs

Os NPCs devem continuar seguindo rotinas coerentes.

Não usar otimizações que provoquem:

- Teletransporte.
- Mudança repentina de direção.
- Animações começando no meio.
- Objetos desaparecendo das mãos.
- Perda de reservas de assento.
- Repetição inesperada de diálogos.

Se houver redução adaptativa de detalhes, aplicar primeiramente a efeitos cosméticos e elementos distantes, nunca à continuidade do roteiro.

## 5.6. Painel de diagnóstico

Adicionar ao Developer Lab uma seção **Desempenho das Cenas**.

Exibir:

- Cena atual.
- FPS.
- P95 e P99.
- Tempo de desenho dos NPCs.
- Quantidade de NPCs.
- Sessões criadas.
- Reconstruções de timeline.
- Uso de memória.
- Tempo de apresentação do bitmap.

Permitir iniciar um benchmark reproduzível e exportar um relatório local, sem envio automático de dados.

## 5.7. Testes e critérios de aceite

Criar:

- `ScenePerformanceBenchmarkTest`
- `VenueNpcPerformanceTest`
- `NpcSessionReuseTest`
- `NpcFrameAllocationTest`
- `SceneTransitionPerformanceTest`

Critérios:

- Sem regressão relevante em relação ao benchmark inicial.
- Sem reconstruções desnecessárias por quadro.
- Sem crescimento contínuo de memória em testes prolongados.
- Sem alteração da ordem visual ou dos movimentos.
- Sem reinício de NPCs durante carinho.
- Sem quedas relevantes de FPS causadas pelo novo sistema de toque.
- Nenhum cenário de benchmark com falha crítica.

---

# 6. H-08 — Replay do Dia de Demonstração

**Prioridade: P1**

## 6.1. Situação atual

O aplicativo já possui:

`engine/demo/DemoDayFactory.kt`

`engine/demo/DemoDiaryAssembler.kt`

`presentation/screens/demo/DemoDayScreen.kt`

O `DemoDayFactory` cria um dia fictício em memória, com sete momentos:

| Horário | Etapa |
|---|---|
| 07:00 | Hoodie acorda em Casa |
| 07:45 | Saída e deslocamento |
| 08:30 | Chegada ao Trabalho |
| 12:00 | Restaurante e almoço |
| 13:00 | Retorno ao Trabalho |
| 18:00 | Viagem de volta |
| 19:00 | Casa e descanso |

A tela já apresenta resumo e mapa.

Porém, passa `ReplayUiState()` sem controles reais de reprodução, de modo que a jornada não evolui automaticamente.

Também observei que `DemoDiaryAssembler` envia uma lista vazia de atividades do Hoodie ao `DiaryAssembler`. Isso limita a capacidade do replay de apresentar ações fictícias específicas do personagem.

## 6.2. Experiência desejada

Ao entrar no Dia de Demonstração, o usuário encontrará um convite para reproduzir uma jornada fictícia.

A tela terá:

- Mapa da Jornada.
- Hoodie percorrendo o caminho.
- Relógio do dia.
- Contexto atual.
- Atividade fictícia do Hoodie.
- Texto curto explicando o momento.
- Controles de reprodução.
- Aviso permanente de que os dados são fictícios.

### Dois modos de apresentação

**Modo 1 — Tour rápido**

Duração-alvo: aproximadamente 50 segundos.

O sistema percorre os sete momentos, mantendo tempo suficiente para o usuário entender cada transição.

Esse modo é ideal para apresentar o aplicativo a alguém que acabou de instalá-lo.

**Modo 2 — Jornada completa**

Reproduz a linha do tempo fictícia em velocidade acelerada, respeitando a sequência temporal.

Reutilizar as velocidades existentes do Diário:

- 5 minutos do dia por segundo.
- 10 minutos do dia por segundo.

Permitir pausa, retomada, reinício e avanço entre eventos.

Não modificar as velocidades do replay real apenas por causa do modo de demonstração.

## 6.3. Arquitetura proposta

Criar:

`engine/demo/DemoReplayTimeline.kt`

`engine/demo/DemoReplayController.kt`

`engine/demo/DemoReplayPresentation.kt`

`presentation/screens/demo/DemoReplayViewModel.kt`

Reutilizar:

- `ReplayUiState`
- `ReplayState`
- `ReplaySpeed`
- `JourneyReplayControls`
- `JourneyReplayAssembler`
- `ReplayHudAssembler`
- `JourneyOverworldModel`
- `JourneyChaptersView`

Evitar copiar toda a lógica de `DiaryViewModel`.

A lógica comum de avanço temporal poderá ser extraída para um controlador puro compartilhado, se a integração demonstrar essa necessidade.

## 6.4. Criar atividades fictícias do Hoodie

Ampliar `DemoDayFactory` para criar uma lista de atividades compatível com o `DiaryAssembler`.

Exemplo de roteiro:

| Horário | Contexto | Atividade do Hoodie |
|---|---|---|
| 07:00 | Casa | Acordando |
| 07:15 | Casa | Café da manhã |
| 07:45 | Deslocamento | Indo ao trabalho |
| 08:30 | Escritório | Trabalhando |
| 10:30 | Escritório | Pausa para café |
| 12:00 | Restaurante | Almoçando |
| 13:00 | Escritório | Trabalhando |
| 16:00 | Escritório | Descansando brevemente |
| 18:00 | Deslocamento | Voltando para casa |
| 19:00 | Casa | Descansando |
| 19:30 | Casa | Assistindo TV |
| 20:00 | Fim da demonstração | Encerramento |

As atividades não deverão ser gravadas no Room.

Serão construídas exclusivamente em memória.

O `DemoDiaryAssembler` passará essas atividades para os montadores reais, fazendo o replay apresentar também a atividade fictícia do Hoodie.

## 6.5. Definir corretamente o dia ativo

O exemplo começa às 07:00, não à meia-noite.

A janela de replay deverá iniciar às 07:00 e terminar às 20:00, enquanto o mapa do dia poderá continuar representando o contexto anterior como sono, caso necessário.

Utilizar uma `DailyActivityWindow` fictícia explícita, em vez de depender da janela civil padrão.

Isso evita uma reprodução inicial longa e vazia.

### Privacidade e isolamento

Não consultar os dados reais do usuário para construir a demonstração.

Não acessar:

- Localização atual.
- Histórico de visitas.
- Dados reais do celular.
- Rotinas aprendidas.
- Contextos persistidos.
- Notificações reais.

O conteúdo deverá permanecer identificado como fictício.

## 6.6. Controlador da reprodução

`DemoReplayController` será responsável por:

- Iniciar.
- Pausar.
- Retomar.
- Reiniciar.
- Avançar para o próximo evento.
- Voltar ao evento anterior.
- Selecionar um instante pela barra temporal.
- Selecionar velocidade.
- Concluir a reprodução.

Estados:

`IDLE → PLAYING → PAUSED → FINISHED`

### Relógio de reprodução

Utilizar tempo monotônico para calcular o avanço.

Não depender de `delay()` como fonte de verdade do relógio: o atraso de renderização não deve fazer o replay perder a sincronização.

O `delay()` poderá ser usado para definir a frequência de atualização visual, mas o instante reproduzido deverá ser calculado pelo tempo decorrido.

### Ciclo de vida

Ao sair da tela:

- Cancelar a corrotina de reprodução.
- Descartar atualizações pendentes.
- Não deixar o replay funcionando em background.

Ao voltar:

- Restaurar o estado permitido pela experiência.
- Não iniciar automaticamente uma reprodução que o usuário havia pausado.

## 6.7. Tour rápido

O tour deverá utilizar um roteiro compacto independente da velocidade da Jornada completa.

Cada etapa terá:

- Instante fictício representado.
- Duração real de apresentação.
- Mensagem narrativa.
- Contexto.
- Atividade do Hoodie.
- Elemento visual destacado.

Exemplo:

**07:00 — Um novo dia**

Hoodie aparece em Casa.

Texto: "O Hoodie percebe que o dia começou e registra o início da rotina."

**08:30 — Chegada ao trabalho**

O personagem percorre o trajeto até o Trabalho.

Texto: "Ao reconhecer um local conhecido, o Hoodie identifica o contexto e apresenta sua rotina paralela."

**12:00 — Hora do almoço**

O marcador chega ao Restaurante.

Texto: "As atividades mudam conforme o ambiente, enquanto a Jornada registra a sequência do dia."

**19:00 — De volta para Casa**

O percurso retorna ao mesmo lugar inicial.

Texto: "O Hoodie reconhece a volta para Casa e continua acompanhando o dia."

**Importante:** as explicações do tour descrevem funcionalidades do aplicativo; não devem alegar que os dados fictícios foram realmente detectados pelo celular.

## 6.8. Preservar lugares repetidos

O dia fictício utiliza a mesma Casa no começo e no fim, e o mesmo Trabalho em dois períodos.

Manter o mesmo `placeId` para essas visitas.

A apresentação do mapa deverá preservar a identidade do lugar e representar a sequência das visitas.

Exemplo:

Casa → Trabalho → Restaurante → Trabalho → Casa.

A interface não deverá tratar cada visita como um estabelecimento novo.

Caso o layout atual utilize nós temporais separados para representar retornos, esses nós deverão compartilhar a identidade visual do estabelecimento e permitir reconhecer a revisita.

## 6.9. Sincronização entre mapa e Hoodie

O timestamp do replay será a fonte única de verdade para:

- Posição no percurso.
- Contexto atual.
- Atividade do Hoodie.
- Período do dia.
- Destaque da etapa.
- Texto narrativo.

Durante o deslocamento, o personagem deverá avançar de forma contínua pelo trecho.

Nas paradas, permanecerá no nó correspondente.

Não usar timers independentes para movimentação, diálogo e troca de cenário.

### Cena contextual

Como evolução visual desta entrega, adicionar uma pequena visualização contextual que acompanhe o capítulo atual, reutilizando o sistema de cenas existente.

Exemplo:

- Casa → cena Casa.
- Trabalho → cena Escritório.
- Almoço → cena Restaurante.
- Deslocamento → cena de transporte.

Essa visualização deverá usar o relógio fictício e não poderá interferir na cena real da Home.

Ela também deve ser opcional em aparelhos com menor capacidade de processamento.

## 6.10. Interface

Modificar:

`presentation/screens/demo/DemoDayScreen.kt`

Proposta de organização:

**Cabeçalho**

- Aviso "Demonstração — dados fictícios".
- Botão de sair.

**Área principal**

- Mapa da Jornada.
- Hoodie se deslocando conforme o tempo.
- Destaque do local atual.

**Informações**

- Horário fictício.
- Contexto.
- Atividade do Hoodie.
- Descrição da etapa.

**Controles**

- Reproduzir / Pausar.
- Anterior / Próximo.
- Reiniciar.
- Barra temporal.
- Seleção de velocidade ou modo.

A interface deverá funcionar também em telas menores, com rolagem quando necessário.

## 6.11. Testes

Criar:

- `DemoReplayControllerTest`
- `DemoReplayTimelineTest`
- `DemoReplayDeterminismTest`
- `DemoReplayIsolationTest`
- `DemoReplayNavigationTest`
- `DemoReplayUiTest`

Casos obrigatórios:

| Cenário | Resultado |
|---|---|
| Iniciar replay | Começa às 07:00 |
| Pausar | Tempo fictício deixa de avançar |
| Retomar | Continua do instante correto |
| Avançar | Vai ao próximo evento |
| Voltar | Vai ao evento anterior |
| Reiniciar | Retorna ao início |
| Chegar ao final | Estado `FINISHED` |
| Alterar velocidade | Tempo avança na velocidade escolhida |
| Buscar horário pela barra | Mapa e HUD ficam sincronizados |
| Voltar para Casa | Retorno ao lugar correto |
| Abrir e fechar a demonstração | Nenhum dado real alterado |
| Trocar a orientação da tela | Estado restaurado coerentemente |
| Sair durante reprodução | Corrotina cancelada |
| Reproduzir novamente | Mesmo resultado para os mesmos dados |

**Critério de aceite:** a demonstração deve conseguir reproduzir a Jornada fictícia inteira, com mapa, contexto, atividades e controles sincronizados, sem gravar informações na base real.

---

# 7. Plano de implementação da sprint

## Fase 1 — Consistência e controle de reações

**Prioridade P0**

Entregas:

- H-06: contexto canônico de apresentação.
- H-05: coordenador único.
- Cancelamento e confirmação efetiva das reações.
- Testes de integração com Home e personagens.

Essa fase deve vir primeiro porque corrige a base compartilhada pelas novas falas.

## Fase 2 — Interações dos novos ambientes

**Prioridade P1**

Entregas:

- H-04: frases completas das seis cenas.
- Novas condições de seleção.
- Compatibilidade com os gestos.
- Testes de catálogo e continuidade.

## Fase 3 — Benchmark e otimizações

**Prioridade P0**

Entregas:

- H-07: métricas para todas as cenas relevantes.
- Relatório de linha de base.
- Identificação dos gargalos.
- Otimizações comprovadas por benchmark.
- Testes de regressão visual e de desempenho.

## Fase 4 — Dia de Demonstração

**Prioridade P1**

Entregas:

- H-08: atividades fictícias.
- Controlador de replay.
- Tour rápido.
- Jornada completa.
- Controles e apresentação.
- Testes de isolamento.

## Fase 5 — Homologação integrada

Executar:

- Testes unitários.
- Testes de integração.
- Testes instrumentados Android.
- Lint.
- Build debug.
- Testes de renderização.
- Matriz visual.
- Benchmark das cenas.
- Validação do Dia de Demonstração.

A funcionalidade só será considerada pronta depois de verificar o comportamento integrado, não apenas a compilação de cada módulo.

---

# 8. Feature flags

Adicionar ou manter controles independentes:

| Flag | Finalidade |
|---|---|
| `HOODIE_TOUCH_INTERACTIONS_V1` | Sistema de toque existente |
| `HOODIE_REACTION_COORDINATOR_V1` | Nova arbitragem de reações |
| `HOODIE_VENUE_INTERACTIONS_V1` | Frases dos estabelecimentos |
| `HOODIE_CONTEXT_PRESENTATION_V1` | Contexto consistente da Home |
| `HOODIE_SCENE_PERF_DIAGNOSTICS` | Métricas extras de desempenho |
| `HOODIE_DEMO_REPLAY_V1` | Reprodução do dia fictício |

Essas flags devem começar desativadas em builds destinados aos usuários até a homologação correspondente.

A instrumentação de desempenho poderá ser controlada por uma configuração exclusiva de diagnóstico, evitando sobrecarga de produção.

---

# 9. Critérios de aceite da sprint

### Interações e personalidade

- [ ] Todas as seis cenas novas possuem falas contextualizadas.
- [ ] Toque rápido e toque longo funcionam conforme o ambiente.
- [ ] Nenhuma frase menciona objetos ausentes.
- [ ] As reações do usuário têm prioridade adequada.
- [ ] Não existem animações conflitantes no Hoodie.
- [ ] Reações não exibidas não são registradas como exibidas.
- [ ] O balão da Home mostra somente uma fala por vez.

### Contexto e continuidade

- [ ] Cena e diálogo utilizam o mesmo contexto de apresentação.
- [ ] Correções manuais cancelam solicitações antigas.
- [ ] Mudanças entre estabelecimentos do mesmo tipo funcionam corretamente.
- [ ] Home office utiliza falas de trabalho.
- [ ] Nenhuma interação altera indevidamente o Diário.

### Desempenho

- [ ] O benchmark contempla todas as cenas relevantes.
- [ ] As métricas permitem identificar planejamento, renderização e apresentação.
- [ ] Não há reconstrução desnecessária das sessões.
- [ ] Não há regressões visuais ou teletransportes de NPCs.
- [ ] O aplicativo mantém fluidez compatível com a meta de aproximadamente 30 FPS.

### Dia de Demonstração

- [ ] Existe reprodução automática.
- [ ] Play, pausa, retomada, reinício e avanço funcionam.
- [ ] O mapa mostra a posição correta do Hoodie.
- [ ] O HUD mostra o contexto e a atividade fictícios.
- [ ] Casa e Trabalho são reconhecidos nos retornos.
- [ ] A demonstração não acessa nem modifica dados reais.
- [ ] O replay é determinístico e pode ser reproduzido novamente.

---

# 10. Resultado esperado

Ao concluir esta sprint, o Hoodie terá três melhorias perceptíveis para o usuário:

**1. Interações mais naturais.** O personagem responderá de forma específica ao ambiente e à atividade, inclusive em Padaria, Shopping, Praia, Cinema, Farmácia e Hotel.

**2. Comportamentos mais consistentes.** Carinho, personalidade, NPCs e diálogos compartilharão uma política coerente de prioridade, sem interromper atividades essenciais.

**3. Uma demonstração visual do aplicativo.** O usuário poderá assistir a um dia fictício completo, com o Hoodie percorrendo o mapa, mudando de contexto e realizando atividades.

A otimização dos NPCs sustentará essas funcionalidades, permitindo ampliar a riqueza dos ambientes sem comprometer a fluidez.

**Diretriz final:** preservar a inteligência de rotina e a autonomia do Hoodie, utilizando interações, personalidade e replay como camadas de apresentação — nunca como fontes de dados fictícios para o Diário real.
