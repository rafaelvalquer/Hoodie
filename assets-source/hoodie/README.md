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
├── baseline/                ← o procedural exportado (ponto de partida — NÃO vai para o app)
│   ├── hoodie_walk.png/.json/.anchors.png   walk_{side,front,back}, walk_backpack_{side,front,back}
│   ├── hoodie_idle.*        idle (8), idle_sit (8), idle_look, idle_ear, idle_scratch (8)
│   ├── hoodie_work.*        sit_down, work_typing, stop_typing, reach_mouse, work_mouse, work_read, stand_up, work_notes, work_tired
│   └── hoodie_sleep.*       bed_sit, bed_lie_down, sleep (6), sleep_turn, wake_eyes, bed_exit
├── tools/import_baseline.lua ← cria um .aseprite vazio a partir de um baseline (para redesenhar do zero)
├── hoodie_*.aseprite        ← ARTE FINAL v1 (vai para o APK via export)
└── export.ps1 / export.sh   ← exporta tudo para app/src/main/assets/pixel/hoodie/
```

## Fonte da verdade: os `.aseprite`

Desde a V0.2 RC os quatro `hoodie_*.aseprite` são a fonte oficial da arte. O APK é
**compilado** deles pelo `AsepriteSourceCompiler` (mesmo formato do `aseprite -b`):

```
hoodie_walk.aseprite ──▶ hoodie_walk.png + hoodie_walk.json + hoodie_walk.anchors.png
```

```bash
# depois de editar um .aseprite (no Aseprite ou por script):
./gradlew :app:testDebugUnitTest -PexportArt=true --tests "*FinalArtExportTest*"
```

Sem a flag, `FinalArtExportTest` e `AsepriteRuntimeParityTest` reprovam se o APK não for
exatamente a compilação dos `.aseprite` (frames, tags, durações, âncoras, paleta, PNG, JSON).
Com o Aseprite instalado, `export.ps1/.sh` produz o mesmo resultado.

**Bootstrap** (`ArtBootstrapStudio`, só testes): cria um `.aseprite` a partir das poses do
procedural + retoque + passes de `ArtPasses`. Nunca sobrescreve um arquivo existente,
exceto quando o grupo é nomeado: `-PartBootstrap=hoodie_walk[,hoodie_idle…]` (ou `all`
para criar só os que faltam).

Camadas semânticas (de baixo para cima): `baseline (referencia)` (referência, escondida e
travada — nunca vai para o APK), `tail`, `leg_left`, `leg_right`, `backpack`, `torso`,
`hoodie`, `hoodie_shadow`, `strings`, `arm_left`, `arm_right`, `hand_left`, `hand_right`,
`head`, `ears`, `face`, `accessory`, `outline`, `anchors`. Esquerda/direita = lado da
imagem. Contorno e rosto são separados pela cor; o resto, pela parte do corpo que o pintor
procedural desenhou.

### Revisão artística — `art-status.json`

```json
{ "walk": { "final": true, "manualReview": true, "pass": "manual-v1", "reviewedBy": "Rafael" } }
```

`manualReview` só vira `true` quando uma pessoa revisar o grupo no Aseprite (preencha
`reviewedBy`). Copie o arquivo também para `app/src/main/assets/pixel/hoodie/` (um teste
confere). **A release (versão sem `-dev`) é bloqueada enquanto houver grupo sem revisão
manual** — a V0.2 não sai só com bootstrap automático. O Pixel Lab mostra "Final asset" e
"Manual reviewed" por grupo, e o modo **Difference** pinta os pixels que mudaram em
relação ao procedural.

Critérios objetivos (ShippedSheetsValidationTest): 48×72, alpha 0/255, pés fixos (drift
≤ 1 px), âncoras críticas presentes (senão o clip é rejeitado), contagem de frames do clip,
paleta limitada (cores extras = aviso), silhueta com jitter ≤ 2 px e cabeça com drift ≤ 2 px
nos loops estáveis.

## Fluxo do artista

1. **Criar o arquivo** (uma vez por grupo de animações):

   ```bash
   aseprite -b --script-param json=assets-source/hoodie/baseline/hoodie_walk.json \
            --script assets-source/hoodie/tools/import_baseline.lua
   ```

   (ou pela interface: *File › Scripts › import_baseline* e escolher o `.json`).
   Sai `assets-source/hoodie/hoodie_walk.aseprite` já com: paleta oficial, as camadas do
   sprite master vazias, a camada **`baseline (referencia)`** travada a 38% com o desenho
   atual, a camada **`anchors`**, as **tags** e a **duração de cada frame**.

2. **Desenhar** por cima da referência, nas camadas do master. Mantenha o número de
   frames de cada tag (os eventos — pegar a caneca, passo, sentar — seguem o índice).

3. **Âncoras**: na camada `anchors`, 1 pixel por âncora em cada frame. Já vêm pintadas do
   baseline; mova o pixel se a mão/cabeça mudar de lugar no seu desenho.

   | âncora | cor | para quê |
   |---|---|---|
   | `feet` | magenta `#FF00FF` | alinhamento no chão — fica em (24, 71) |
   | `head` | amarelo `#FFFF00` | Zzz, suor, brilho |
   | `right_hand` | vermelho `#FF0000` | caneca, celular, garrafa… (o jogo desenha o item aqui) |
   | `left_hand` | azul `#0000FF` | halter na outra mão |
   | `back` | verde `#00FF00` | mochila |

4. **Exportar**: `.\assets-source\hoodie\export.ps1` (ou `export.sh`). Para cada
   `.aseprite` saem `<nome>.png` + `<nome>.json` (sem a referência e sem as âncoras) e
   `<nome>.anchors.png` (só as âncoras), direto em `app/src/main/assets/pixel/hoodie/`.

5. **Validar**: `./gradlew :app:testDebugUnitTest --tests '*ShippedSheetsValidationTest*'`
   falha se algo fugir do padrão (lista abaixo). No app, **Pixel Lab › Sprites** mostra o que
   foi carregado e os problemas; **Pixel Lab › Animação** compara frame a frame com
   onion-skin, âncoras, bounding box e linha dos pés.

## Sprite master — camadas

| camada | conteúdo |
|---|---|
| `outline` | contorno #1A1C33, 1 px, por forma |
| `fur` / `fur_shadow` | pelo #86A9E8 / sombra #6586CE (luz vem da esquerda) |
| `hoodie` / `hoodie_shadow` | moletom #B9CBEF / #92A9DB, barra canelada #6E84BE |
| `face` | olhos 3×4 #0F1124 com brilho branco no canto superior esquerdo, nariz + "w" #283063 |
| `arms`, `legs` | mangas (de perfil, a manga da frente é #DAE5FA, mais clara que o tronco), patas de pelo |
| `ears` | orelhas com interior #4E69B0 |
| `strings` | cordões #F1F5FF (animados com atraso — follow-through) |
| `accessory` | mochila #DB7A3E / #AA5329 (oculta quando não usada) |
| `anchors` | marcadores de âncora (não aparecem no jogo) |

Itens na mão (caneca, celular, garrafa…) **não** entram no sprite: o jogo desenha o item
na âncora `right_hand` do frame.

## Convenções

- **Tamanho**: 48×72 por frame, fundo transparente. Os pés tocam o pixel (24, 71).
- **Tags** = id da animação em minúsculas + vista: `walk_side`, `walk_front`, `walk_back`,
  `idle`, `work_typing`, `sleep`, `bed_lie_down`… (lista completa: `AnimationId`).
  Sem sufixo = frente. **`_side` é desenhado olhando para a ESQUERDA**; a direita é espelhada.
- **Duração**: a de cada frame no Aseprite vale no jogo (ex.: caminhada 100/80/80/100/100/80/80/100 ms).
- **Âncoras**: camada `anchors` (preferida). Slices `feet/head/right_hand/left_hand/back`
  também funcionam, mas a API do Aseprite não anima slices por frame pelo script.

## Critérios de aceite (verificados em `ShippedSheetsValidationTest`)

- frame 48×72, duração > 0, tag reconhecida, mesmo número de frames do clip;
- pés em y = 71 e x entre 22 e 26 (nada de pé deslizando);
- sem anti-aliasing (pixel opaco ou transparente);
- paleta: as 17 cores do Hoodie + no máximo 7 extras;
- chão estável (bounding box) — exceto clips com pulo ou deitado.

O mesmo teste roda sobre `baseline/`, provando que as regras aceitam arte boa.

## O que muda ao trocar um clip por desenho

O procedural aplica por cima de qualquer clip **piscadas, olhares, orelhas e expressões**
(o cansaço no café, por exemplo). Um sprite sheet é a imagem final: o que não estiver
desenhado não aparece. Para o idle continuar vivo, desenhe as variações como clips
próprios (`idle_look`, `idle_ear`, `idle_scratch`) e inclua uma piscada dentro do
próprio `idle`. Clips sem desenho continuam procedurais, com tudo isso funcionando.

## Ordem sugerida

1. `walk_side`, `walk_front`, `walk_back` (+ `walk_backpack_*`)
2. `idle`, `idle_look`, `idle_ear`, `idle_scratch`
3. `sit_down`, `stand_up`, `bed_sit`, `bed_lie_down`, `sleep`, `wake_eyes`, `bed_exit`
4. `work_typing`, `work_mouse`, `reach_mug`, `drink`, `put_mug`
5. o restante, por grupo

## Revisão manual aprovada — 02/10/2026

Rafael revisou e aprovou os quatro grupos: walk, idle, sleep e work. A confirmação foi fornecida pelo usuário neste chat. A galeria em docs/art-review.html conserva os sprites e os hashes das fontes apresentadas. Os dois art-status.json registram manualReview=true, pass=manual-v1 e reviewedBy=Rafael após a sincronização dos assets.

