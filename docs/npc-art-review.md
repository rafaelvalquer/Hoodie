# Revisão da arte NPC V3 — candidata

Esta revisão aproxima gatos, cães, buldogue e guaxinim da cabeça retangular de cantos arredondados do Hoodie. Coelho, rato e pato mantêm silhuetas próprias, com o mesmo contorno e densidade de pixels. Nas cenas, os personagens ambientais são reduzidos uniformemente para 80% e mantêm os pés alinhados; as folhas comparativas mostram os sprites em tamanho integral para facilitar a análise das proporções.

Os PNGs e hashes candidatos foram regenerados. `app/src/test/resources/npc-art-v2.sha256` agora guarda os hashes da arte V3 como teste de regressão visual; mudanças futuras de desenho devem atualizar essa referência intencionalmente.

Cada folha compara Hoodie à esquerda e NPC à direita. As linhas seguem esta ordem: Bulldog, Dog, Rabbit, Mouse, Duck, Raccoon e Cat. As três colunas mostram frente, perfil e costas. As folhas cobrem IDLE, WALK, LOOK, TALK, SIT_PHONE, SIT_EAT e SIT_SLEEP.

## Espécies e animações

### IDLE

![Comparação de todas as espécies NPC em IDLE](npc-art-review/species-idle.png)

### WALK

![Comparação de todas as espécies NPC em WALK](npc-art-review/species-walk.png)

### LOOK

![Comparação de todas as espécies NPC em LOOK](npc-art-review/species-look.png)

### TALK

![Comparação de todas as espécies NPC em TALK](npc-art-review/species-talk.png)

### Sentados usando o telefone

![Comparação de todas as espécies NPC sentadas usando o telefone](npc-art-review/species-sit_phone.png)

### Sentados comendo

![Comparação de todas as espécies NPC sentadas comendo](npc-art-review/species-sit_eat.png)

### Sentados cochilando

![Comparação de todas as espécies NPC sentadas cochilando](npc-art-review/species-sit_sleep.png)

## Variações adicionais do catálogo

Estas quatro identidades reaproveitam as sete espécies-base, com traje e paleta próprios: Dog Shopper, Rabbit Reader, Rabbit Walker e Cat Guest. As folhas mostram Hoodie e cada variação em frente, perfil e costas para todo o conjunto comum de animações.

### Variações em IDLE

![Dog Shopper, Rabbit Reader, Rabbit Walker e Cat Guest em IDLE e três orientações](npc-art-review/identities-idle.png)

As folhas das outras animações: [WALK](npc-art-review/identities-walk.png), [LOOK](npc-art-review/identities-look.png), [TALK](npc-art-review/identities-talk.png), [SIT_PHONE](npc-art-review/identities-sit_phone.png), [SIT_EAT](npc-art-review/identities-sit_eat.png) e [SIT_SLEEP](npc-art-review/identities-sit_sleep.png). As cenas reais de compras, restaurante, trem e passeio acima também mostram esses perfis integrados.

## Bulldog — personagem de referência

### IDLE de frente

![Bulldog executivo em IDLE de frente, comparado ao Hoodie](npc-art-review/bulldog_idle_front.png)

### WALK de perfil

![Bulldog executivo andando de perfil, comparado ao Hoodie](npc-art-review/bulldog_walk_side.png)

### TALK

![Bulldog executivo falando, comparado ao Hoodie](npc-art-review/bulldog_talk.png)

### Terno em cena diurna

![Bulldog executivo com terno em cena diurna](npc-art-review/bulldog_suit_day.png)

### Escritório

![Cena do escritório com Bulldog](npc-art-review/bulldog-office-scene.png)

### Mouse sentado usando o telefone

![Mouse sentado com o telefone junto à mão, comparado ao Hoodie](npc-art-review/mouse-sit-phone.png)

## NPCs dentro das cenas

Prévia composta pelo renderer de produção com fundo, objetos, iluminação, camadas e NPCs em escala ambiente de 80%. Inclui escritório nas duas variantes, ônibus, trem, metrô, restaurante, compras e passeio de dia; escritório, ônibus, metrô e restaurante também à noite.

![Galeria dos NPCs renderizados nas cenas reais](npc-art-review/scenes-with-npcs.png)

## Regressão das cenas com NPCs

As cenas de compras e passeio, além de ônibus, trem e metrô, foram renderizadas novamente para refletir a escala menor dos NPCs. Os arquivos de hash em `app/src/test/resources` agora correspondem a estes renders e protegem a nova composição.

Os pares Hoodie/NPC também foram revisados; as identidades ambientais mudaram de forma intencional e os digests correspondentes foram atualizados.

### Escola, compras, família e passeio

![Candidatos de golden das cenas novas, incluindo o comprador NPC](npc-art-review/new-place-scenes-candidate.png)

Relatório de render: [scene-goldens-v1-candidate.sha256](npc-art-review/scene-goldens-v1-candidate.sha256).

### Transporte

![Candidatos de golden das cenas de transporte com passageiros NPC](npc-art-review/transport-scenes-candidate.png)

Relatório de render: [transport-scenes-v1-candidate.sha256](npc-art-review/transport-scenes-v1-candidate.sha256).

### Restaurante

![Gato convidado sentado comendo na cena de restaurante](npc-art-review/scene-restaurant-v0.png)

## Digests da arte NPC

`npc-art-review/npc-art-v2-candidate.sha256` registra os pixels exibidos acima. Os mesmos digests estão em `app/src/test/resources/npc-art-v2.sha256` e são verificados por `NpcArtGoldenTest`.
