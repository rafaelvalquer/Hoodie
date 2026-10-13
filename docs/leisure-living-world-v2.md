# Passeio 2.0 — Leisure Living World V2

Flag: `HoodieConfig.LEISURE_LIVING_WORLD_V2` (desligada = comportamento antigo, `LeisureNpcDirector.planLegacy`).
Escritório, Restaurante e Mercado não mudam.

## Peças (`pixel/npc/leisure/`)
- **LeisureNpcSession** — dona da simulação. Gera uma linha do tempo conjunta (segmentos STAY/WALK/TURN) para
  todos os visitantes, sempre avançando o cursor mais atrasado. A mesma chave (`daySeed`, variante, noite) e o
  mesmo instante dão a mesma cena, em qualquer taxa de quadros e em replay. Histórico podado (2048 segmentos
  por visitante); consultas antes da janela guardada re-simulam do zero.
- **LeisureNpcBrain** — escolhe a atividade pelos pesos da personalidade (Explorador, Turista, Calmo), com
  cooldowns, no máximo 2 repetições seguidas e saída ao fim da visita.
- **LeisureNavigationGraph** — pontos (entradas, caminho, banco, mirante, atração, conversa) e arestas por
  variante; rotas BFS em cache (nenhuma rota alocada por quadro). Obstáculos: banco, poste, mirante, lago, chafariz.
- **LeisureSpotReservation** — um ocupante por ponto. Toda parada reserva o próprio intervalo; o banco tem um
  assento e o Hoodie tem prioridade (reservas do Hoodie vêm de `RenderFrame.occupancy`).
- **LeisureSocialCoordinator** — um encontro por vez, cooldown de 60–100 s, falas alternadas ≤ 22 caracteres,
  um balão por vez na cena.
- **LeisureAmbientState** — patos vão à margem, o chafariz sobe e pisca na foto, a borboleta circula quem observa;
  `LeisureScene.props()` lê via `SceneEnv.leisureAmbient`.

## Banco
- O Hoodie começa a querer o banco → o plano do visitante é cortado em `GRACE_MS` (300 ms): termina o gesto,
  levanta (STAND_UP 640 ms), vira, desce e vai para um ponto livre (ou sai da cena se nenhum estiver livre).
- Hoodie já sentado quando a cena abre → o rato começa no caminho, nunca sentado.

## Semente
`VisualState.daySeed` (dia da jornada, `epochDay`) define a história; `variant` define o cenário. Só no Passeio.

## Testes
`Leisure*Test` em `app/src/test/java/com/hoodie/app/pixel/`; folhas de revisão em `build/pixel-preview/leisure-v2/`.
Desempenho (JVM, 10 min a 30 FPS): p95 da simulação ~10 µs; quadro completo ~0,85 ms (legado ~0,87 ms).
Goldens do Passeio (`leisure_walk`, `leisure_bench`, `leisure_photo`, `scene_leisure`) só são regravados após
aprovação humana das folhas.
