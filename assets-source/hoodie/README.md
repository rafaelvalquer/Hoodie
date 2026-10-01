# Hoodie — fontes de sprite (Aseprite)

O jogo funciona inteiro com o **pintor procedural** (`HoodiePainter`). Sprite sheets
desenhados à mão substituem animações **uma a uma**: a máquina de estados pede
`AnimationId + Direction + frame`, e o `CompositeSpriteProvider` entrega o sheet se ele
existir, senão o procedural. Nada no Context Engine, Hoodie Engine ou cenas muda.

```
AnimationStateMachine → SpriteProvider ─┬─ SpriteSheetProvider  (estes arquivos, quando existirem)
                                        └─ ProceduralSpriteProvider (fallback/debug)
```

## Arquivos

```
assets-source/hoodie/
├── hoodie_master.aseprite   ← referência de proporção/paleta (camadas abaixo)
├── hoodie_idle.aseprite
├── hoodie_walk.aseprite
├── hoodie_work.aseprite
├── hoodie_sleep.aseprite
├── hoodie_home.aseprite
├── hoodie_food.aseprite
├── hoodie_gym.aseprite
├── baseline/                ← exportado do procedural (ponto de partida, NÃO vai para o app)
└── export.ps1 / export.sh   ← exporta tudo para app/src/main/assets/pixel/hoodie/
```

Os `.aseprite` ainda não existem: precisam ser desenhados por um artista. Comece
importando um PNG de `baseline/` (Aseprite → *File › Import Sprite Sheet*, células 48×72)
e redesenhe por cima, mantendo as tags.

## Sprite master — camadas

Todas as animações partem de `hoodie_master.aseprite`, para o gato nunca virar outro gato:

| camada | conteúdo |
|---|---|
| `outline` | contorno #1A1C33, 1 px, por forma |
| `fur` / `fur_shadow` | pelo #86A9E8 / sombra #6586CE (luz vem da esquerda) |
| `hoodie` / `hoodie_shadow` | moletom #B9CBEF / #92A9DB, barra canelada #6E84BE |
| `face` | olhos 3×4 #0F1124 com brilho branco no canto superior esquerdo, nariz + "w" #283063 |
| `arms`, `legs` | mangas do moletom, patas de pelo |
| `ears` | orelhas com interior #4E69B0 |
| `strings` | cordões #F1F5FF (animados com atraso — follow-through) |
| `accessory` | mochila #DB7A3E / #AA5329 (oculta quando não usada) |
| `anchors` | **slices** (ver abaixo), não pixels |

Paleta total: as 17 cores de `HoodiePalette` (o teste `paleta limitada` exige ≤ 24).
Itens na mão (caneca, celular, garrafa…) **não** entram no sprite: o jogo desenha o item
na âncora `right_hand` do frame.

## Convenções

- **Tamanho**: 48×72 por frame, fundo transparente. Os pés tocam o pixel (24, 71).
- **Tags** = id da animação em minúsculas + vista: `walk_side`, `walk_front`, `walk_back`,
  `idle`, `work_typing`, `sleep`, `bed_lie_down`… (lista completa: `AnimationId`).
  Sem sufixo = frente. **`_side` é desenhado olhando para a ESQUERDA**; a direita é espelhada.
- **Duração**: a duração de cada frame no Aseprite é usada no jogo (ex.: caminhada
  100/80/80/100/100/80/80/100 ms).
- **Número de frames**: mantenha o mesmo número de frames do clip procedural — os
  eventos (`MUG_PICKUP`, `SIT`, `FOOTSTEP`…) são lidos do clip pelo índice.
- **Âncoras** = slices com estes nomes (pivot ou centro do retângulo):
  `feet`, `head`, `right_hand`, `left_hand`, `back`. Use chaves de slice por frame
  quando a mão se move.

## Exportar

```powershell
.\assets-source\hoodie\export.ps1
```

ou manualmente:

```bash
aseprite -b hoodie_walk.aseprite --sheet hoodie_walk.png --data hoodie_walk.json \
         --format json-array --list-tags --list-slices --sheet-type horizontal
```

Coloque o `.png` + `.json` em `app/src/main/assets/pixel/hoodie/`. Na próxima abertura
do app, **Pixel Lab › Sprites** mostra o que foi carregado e qualquer problema
(tag desconhecida, frame fora de 48×72), e **Pixel Lab › Animação** mostra a fonte de cada
clip, com onion-skin, âncoras, bounding box e linha dos pés.

## Ordem sugerida (do plano)

1. `walk_side`, `walk_front`, `walk_back` (+ `walk_backpack_*`)
2. `idle`, `idle_look`, `idle_ear`, `idle_scratch`
3. `sit_down`, `stand_up`, `bed_sit`, `bed_lie_down`, `sleep`, `wake_eyes`, `bed_exit`
4. `work_typing`, `work_mouse`, `reach_mug`, `drink`, `put_mug`
5. o restante, por grupo

Critério de aceite (também verificado em teste): sem mudança de proporção, rosto no
mesmo lugar, outline consistente, sem anti-aliasing, pés sem deslizar, item preso na mão.
