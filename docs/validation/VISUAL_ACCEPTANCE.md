# Aceite visual

Revisão feita no **Developer Lab → aba PIXEL** (build debug) e nas exportações PNG geradas pelos
testes `SpritePreviewTest` / `ScenePreviewTest` em `app/build/pixel-preview/`.

## Regras gerais

- Pixel-perfect: escala inteira, `FilterQuality.None`, sem interpolação nem anti-aliasing.
- Contorno de 1 px; proporção, rosto e moletom idênticos em todos os frames (`AssetValidationTest`).
- Toda animação tem pelo menos 1 frame; animações sem loop terminam num frame neutro.

## Direção

| Item | Critério |
|---|---|
| Caminhada frontal | `WALK_FRONT` usado quando o alvo está abaixo (|dy| ≥ |dx|, dy > 0) |
| Caminhada de costas | `WALK_BACK` quando o alvo está acima — sem rosto visível |
| Caminhada lateral | `WALK_SIDE` olhando para a esquerda; direita = mesmo sprite com `flipX` |
| Mochila | `WALK_*_BACKPACK` nas três direções, alça visível de frente, mochila visível de costas |

## Transições

| Grupo | Sequência |
|---|---|
| Deitar | `WALK → BED_SIT → BED_LIE_DOWN → SLEEP` |
| Acordar | `SLEEP → WAKE_UP → BED_SIT → BED_EXIT → MORNING_STRETCH` |
| Trabalho | `SIT_DOWN → loop (TYPING 45%, MOUSE 15%, READ 15%, COFFEE 8%, THINK 7%, PHONE 5%, STRETCH 5%) → STAND_UP` |
| Restaurante | `SIT_TABLE → LOOK_MENU → EAT → FINISH_FOOD → STAND_TABLE` |
| Academia | `GYM_WARMUP → RUN/LIFT/WATER/STRETCH → GYM_REST` |
| Celular | `PHONE_TAKE → PHONE_READ/PHONE_SCROLL/PHONE_TYPE → PHONE_PUT` |

## Cenas × período

Cada cena precisa ser conferida em **manhã, dia, entardecer e noite**:

| Cena | Manhã | Dia | Entardecer | Noite |
|---|---|---|---|---|
| Casa (sala) | ☐ | ☐ | ☐ | ☐ |
| Casa (quarto) | ☐ | ☐ | ☐ | ☐ |
| Escritório | ☐ | ☐ | ☐ | ☐ |
| Home office | ☐ | ☐ | ☐ | ☐ |
| Rua / ônibus | ☐ | ☐ | ☐ | ☐ |
| Restaurante | ☐ | ☐ | ☐ | ☐ |
| Academia | ☐ | ☐ | ☐ | ☐ |
| Lugar desconhecido | ☐ | ☐ | ☐ | ☐ |
