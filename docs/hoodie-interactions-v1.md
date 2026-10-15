# Interações com o Hoodie V1

Implementação do plano aprovado: toque no personagem para uma reação contextual e toque longo para carinho temporário. A rotina, as necessidades e o Diário não são modificados.

## Ativação

`HoodieConfig.HOODIE_TOUCH_INTERACTIONS_V1` está `false`, conforme o valor inicial do plano técnico. Para validar em builds internos, alterar a constante para `true`; a ativação destinada aos usuários depende de homologação. O relatório de 14/10/2026 registra a validação da implementação anterior, que estava ativada. `HoodieSceneView` acrescenta somente parâmetros opcionais; as telas existentes continuam usando o mesmo contrato. Com a flag desligada não instala gestos nem ações de acessibilidade e o catálogo é uma dependência lazy da ViewModel.

## Motor e catálogo

`engine/interaction` contém modelos, configuração, política, resolver determinístico, histórico, sessão e hit test. O asset `metadata/interactive_dialogues.json` contém 110 regras e as 108 frases distintas aprovadas. O fixture `hoodie-interaction-required-phrases.txt` conserva a lista de frases do plano para verificar a integridade do catálogo.

Todas as regras possuem os nove campos do esquema aprovado. `activity: null` permite que a condição da animação efetivamente apresentada determine a microação, inclusive quando uma pausa para café ocorre dentro da atividade de trabalho; `scene: null` identifica o fallback global. A ausência de qualquer campo é rejeitada pelo parser.

O contexto utiliza a animação e os elementos do último frame apresentado. O renderer informa proximidade e presença efetiva dos NPCs do escritório e a variante que efetivamente desenha a borboleta. Trabalho na cena HOME usa as regras de Escritório. Mercado e Loja possuem regras separadas.

Sono, transição, Home inativa e reação pendente/ativa bloqueiam novos gestos. Carro, bicicleta e modalidade desconhecida permanecem bloqueados. Transporte de passageiro exige parada observada recentemente, modalidade confirmada ou escolhida pela pessoa e ausência de movimento; toque longo permanece desabilitado. Na dúvida a interação é bloqueada.

O histórico existe apenas na sessão da Home. Intervalo de gestos: 900 ms; fala: 10 s; histórico: três frases; balão: 3,5 s; carinho: até 3 s; validade pendente: 2 s desde a aceitação do gesto, incluindo a entrega ao motor. Cada solicitação recebe um ID próprio, instante de criação e prazo monotônico. O início confirma a fala apenas dentro desse prazo; uma sequência já iniciada pode terminar normalmente. Quando não há variante fora do histórico, a resposta é silenciosa. Nenhuma fila adicional de reações é criada.

## Captura e continuidade

O alvo converte coordenadas da área centralizada usando a mesma escala inteira de `PixelImage` e testa os pixels efetivamente apresentados. Uma máscara opcional de autoria do renderer distingue Hoodie, cenário, objetos e NPCs, inclusive quando um objeto em primeiro plano pinta a mesma cor do personagem. Dois buffers alternados evitam alocação por frame e preservam a máscara apresentada durante o próximo desenho; com interações desligadas não são alocados. O fallback sem renderer usa a transparência do sprite. Não publica frames em StateFlow. Toque longo usa o timeout do Android, consome a liberação sem produzir um toque rápido e cancela em arraste, consumo por outro gesto ou saída do personagem. Ações equivalentes de acessibilidade: “Tocar no Hoodie” e “Fazer carinho no Hoodie”.

`AnimationStateMachine.interact` recebe uma sequência inteira, com validade limitada e identidade de cena. Aguarda o limite seguro; EAT, DRINK, LIFT e WATER aguardam o ciclo. A reprodução pausa o relógio da microação e aplica uma composição temporária ao sprite atual, preservando suporte, orientação, posição, mãos ocupadas e objetos. Ao terminar retoma o mesmo cursor, sem reiniciar eventos de comida, página ou pagamento.

PET_HEAD (600 ms), PURR (1.200 ms) e NUZZLE (700 ms) são clips procedurais registrados, sem eventos de objetos e com postura herdada. A sequência é limitada a 2,5 s para toque e 3 s para carinho. O renderer continua atualizando os NPCs e o ambiente durante a reação.

O movimento de respiração/salto do clip acompanha a postura e desloca as âncoras de cabeça e mãos com o corpo, mantendo pés e apoio do assento. Um sprite sheet pode substituir individualmente um clip em postura de frente, em pé e sem objetos; usa sua própria contagem e duração de frames. Posturas ocupadas ou sentadas mantêm a composição herdada para conservar objetos e apoio. Eventos declarados no sheet não alteram o estado de objetos durante uma interação.

A ViewModel reserva uma solicitação por sessão; a fala e o histórico só são confirmados quando o renderer apresenta o início real da reação. A Home utiliza `InteractiveSpeechState` para priorizar a fala interativa por 3,5 segundos e depois retornar à fala automática vigente, ou ficar sem balão. Respostas silenciosas não apagam a fala nem reiniciam o prazo. Troca de contexto/cena e saída da Home invalidam reservas e cancelam a sequência, impedindo reprodução de falas antigas.

## Sons

Efeitos aprovados da v2 (`assets-source/hoodie/audio/v2`, síntese original) em `res/raw`: três toques, dois inícios de
carinho, ronronar e finalização. `InteractionSoundPlan` decide o que toca e quando, de forma pura: o toque toca um "pop"
no início, alternando as três afinações pelo ID da solicitação (nunca a mesma em seguida); o carinho toca cada etapa
no início da sua animação (PET_HEAD → PURR → NUZZLE), com o mesmo encurtamento da reprodução e só para as etapas
presentes na resposta.

`HoodieInteractionSounds` (SoundPool, canal de mídia, volume 0,8) toca no mesmo instante em que a fala é confirmada:
quando o renderer apresenta o início real da reação. Troca de cena/contexto, cancelamento e saída da Home param os sons
agendados e os que estão tocando. Celular no silencioso ou no vibrar: nenhum som. Ajustes → "Sons" desliga tudo
(`AppSettings.interactionSoundsEnabled`, padrão ligado); o interruptor só aparece com as interações ativas. Flag
`HoodieConfig.HOODIE_INTERACTION_SOUNDS`; como os sons dependem das interações, continuam mudos enquanto
`HOODIE_TOUCH_INTERACTIONS_V1` estiver desligada.

## Verificação

Consultar `docs/validation/hoodie-interactions-v1.md` para evidências e pendências. A validação contempla testes JVM, lint, build, instrumentação no Pixel 8 emulado, matriz visual e ações com TalkBack ativo, além das falhas instrumentadas anteriores citadas pelo plano. Em 14/10/2026 o usuário dispensou explicitamente os testes em aparelho físico para esta entrega.

