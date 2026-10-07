# Bíblia de arte — cenas de transporte (V3)

Vale para **carro, trem, metrô e ônibus**. Toda regra marcada com ✔ tem um teste
correspondente em `TransportSceneQualityTest` (Marco 2); as outras são de revisão humana.

## 1. Escala
- ✔ **1:1 sempre.** Hoodie (48×72) e NPCs nunca são redimensionados dentro de veículos.
- A escala de fundo (0,90/0,95 do `AmbientScale`) vale **só para NPCs** no fundo, nunca para o Hoodie.
- Consequência: no canvas de 240×320 o Hoodie ocupa ~22% da altura. **Quem cresce é o veículo**, não o Hoodie:
  a câmera aproxima, e o veículo pode passar das bordas.

## 2. Contorno e luz
- Contorno `#1A1C33` de 1 px por forma, igual ao sprite.
- Luz vindo da **esquerda**: realce na borda esquerda/superior, sombra à direita/abaixo.
- À noite, o que acende fica na camada `emissive` (janelas, faróis, letreiros) e é reaplicado depois da tinta.

## 3. Paleta
- ✔ **Máximo de 24 cores por cena** (fora o Hoodie e os NPCs).
- ✔ **Nada grande em azul perto do Hoodie.** Nenhuma superfície com mais de 600 px pode ter matiz a menos de
  25° do azul do Hoodie (`HoodiePalette.FUR`, ~219°). Veículos e estofados ficam em tons **quentes**:
  vermelho, mostarda, verde-oliva, vinho. O azul fica restrito ao céu, que é recortado pela janela e distante.
- ✔ **Contraste do Hoodie.** A diferença média de luminância e matiz entre o Hoodie e um anel de 3 px em volta
  dele precisa passar do limiar. Isso reprova carro azul e banco azul.
- ✔ **Sem painel chapado.** Nenhum retângulo de uma só cor com mais de ~600 px fora do céu, medido na cena como
  o usuário vê: textura, costura, rebite, reflexo ou dithering. Sombra profunda e contorno (luma < 50) ficam de
  fora — são sólidos por convenção. Só conta como painel um retângulo com os dois lados de 6 px ou mais;
  frisos, molduras e barras são linhas.

## 4. Profundidade
Três planos de paralaxe, mais o veículo, mais um oclusor:

| Plano | Camada | Movimento |
|---|---|---|
| Longe | `bg_far` | paralaxe lenta |
| Meio | `bg_mid` | paralaxe média |
| Perto | `bg_near` | paralaxe rápida (postes, pilares) |
| Veículo | `vehicle_back` / `vehicle_front` | balanço do `TransportMotion` |
| Oclusor | `vehicle_front` / `foreground` | balanço; **sempre presente** |

O **oclusor em primeiro plano** (porta do carro, encosto, barra vertical, NPC em pé cortado pela borda) é o que dá
profundidade em pixel art pequena. Toda cena precisa de um.

## 5. Enquadramento
- **Câmera fechada.** O veículo pode e deve ser cortado pelas bordas do canvas.
- ✔ **Ocupação.** Pelo menos **25% da altura** é "o Hoodie e o lugar onde ele está" (janela + banco + porta).
- **Menos coisas, maiores.** Nada de mostrar o veículo inteiro com tudo minúsculo.
- ✔ **Sem sobreposição de atores.** Bboxes não se sobrepõem além do que o slot permite; todo ator sentado tem
  o quadril dentro de um slot.

## 6. Camadas (`assets-source/scenes/transport/<cena>.aseprite`)
De baixo para cima, com estes nomes exatos:

| Camada | Conteúdo |
|---|---|
| `bg_far`, `bg_mid`, `bg_near` | Tiles horizontais sem emenda, com largura de 240 px ou mais |
| `vehicle_back` | Interior, vidro, banco de trás |
| `actors` | **Vazia.** O renderer insere o Hoodie e os NPCs aqui |
| `vehicle_front` | Porta, encosto da frente, barras |
| `foreground` | Oclusores soltos (NPC em pé, retrovisor) |
| `emissive` | O que acende à noite |
| `masks` | Recorte das janelas (onde a paralaxe aparece) |
| `slots` | 1 pixel por âncora |

## 7. Slots (camada `slots`, 1 px cada)

| Slot | Cor | Uso |
|---|---|---|
| `seat_hip` | `#00FFFF` | Quadril do Hoodie sentado (mesma cor da âncora do sprite) |
| `steering` | `#FF0000` | Mão direita do Hoodie no volante (cor da `right_hand`) |
| `pole_grip` | `#0000FF` | Mão do Hoodie na barra (cor da `left_hand`) |
| `npc_seat_N` | `#FF80xx` (N = xx) | Quadril de cada NPC sentado |
| `npc_pole_N` | `#80FFxx` (N = xx) | Mão de cada NPC em pé |

## 8. Revisão e aprovação
- Os `.aseprite` de rascunho são **pintados por código** (bootstrap) e servem de ponto de partida.
  O Rafael repinta no Aseprite.
- `scene-art-status.json` (mesmo formato do `art-status.json`). **A release fica bloqueada** se alguma cena de
  transporte não tiver `manualReview=true`.
- **A inspeção do agente não conta como aprovação.** Goldens de cena só são regravados quando a cena tem revisão
  humana registrada.
- O teste de **silhueta** exporta a cena com o veículo em preto sólido para o revisor confirmar que dá para reconhecer
  o que é. Ele só exporta, não aprova.

## 9. Thumbnails (Marco 1)
Escolha do Rafael (07/10/2026): **Carro B** (três quartos, carro inteiro na largura), **Trem A** (três lugares,
Hoodie no meio) e **Metrô A** (sentado, túnel na janela).

`docs/transport-art/thumbnails/` tem 3 propostas por cena, em 3 cinzas, com o sprite real do Hoodie em 1:1
(`TransportThumbnailExportTest`, `-PtransportThumbs=true`). O Rafael escolhe uma letra por cena antes de qualquer
arte.

## 10. Quadro de referências (a montar)
Sugestões para buscar e colar em `docs/transport-art/references/` (só para estudo interno, sem redistribuir).
**Ainda não verificadas imagem por imagem.** Confira antes de usar cada uma como alvo.
1. *Eastward* (Pixpil): interiores com câmera fechada e oclusores no primeiro plano.
2. *Pokémon Black/White*: interior do vagão do metrô (Battle Subway), com bancos longitudinais e barras.
3. *Katana ZERO*: sequências em veículo com paralaxe forte e paleta contida.
4. *The Last Night*: veículos e janelas com várias camadas de profundidade.
5. *Stardew Valley*: cena do ônibus, para escala de veículo × personagem em pixel art.
6. *EarthBound / MOTHER 3*: cenas de viagem com enquadramento lateral simples e legível.
7. *Kingdom Two Crowns*: paralaxe horizontal de 3+ planos sem emenda.
8. *VA-11 Hall-A*: paleta quente com azul restrito e contraste do personagem com o fundo.

O que observar em cada referência: quanto do veículo aparece, qual é o oclusor do primeiro plano, quantas cores a cena
tem e onde o azul aparece.
