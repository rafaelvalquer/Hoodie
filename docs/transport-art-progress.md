# Cenas de transporte V3 — andamento

Regras de arte: [transport-art-bible.md](transport-art-bible.md). Escolha de enquadramento (07/10/2026):
Carro B, Trem A, Metrô A. Ônibus (incluído depois): par de bancos de frente, janela atrás, encosto da fileira da
frente e barra no primeiro plano — enquadramento do plano, sem rodada de thumbnails.

| Cena | Estado | Fonte | Revisão humana |
|---|---|---|---|
| Carro | aprovado (bootstrap-v2) | `assets-source/scenes/transport/car.aseprite` | aprovado no chat em 07/10/2026; v2 aprovada em 09/10/2026 |
| Trem | aprovado (bootstrap-v1) | `assets-source/scenes/transport/train.aseprite` | aprovado no chat em 07/10/2026 |
| Metrô | aprovado (bootstrap-v1) | `assets-source/scenes/transport/metro.aseprite` | aprovado no chat em 07/10/2026 |
| Ônibus | aprovado (bootstrap-v1) | `assets-source/scenes/transport/bus.aseprite` | aprovado no chat em 07/10/2026 |

Interiores (trem, metrô, ônibus): Hoodie sentado de frente com o quadril no slot `seat_hip`, passageiros nos
slots `npc_seat_N` (SIT_FRONT, escala 1:1), cabine com luz própria à noite (só as janelas escurecem). O passageiro
em pé do primeiro plano é uma silhueta pintada na arte (`vehicle_front`), sem mexer no sistema de NPCs.

## Como funciona
- `SceneBootstrapStudio` (teste, `-PsceneBootstrap=<cena>|all`) pinta o **rascunho** em camadas e grava o
  `.aseprite`. Daí em diante o arquivo é a fonte da verdade: o Rafael repinta no Aseprite.
- `-PexportSceneArt=true` valida (camadas, slots, ≤ 24 cores) e grava a cópia normalizada em
  `app/src/main/resources/pixel/scenes/transport/`, lida igual no app e na JVM.
- `TransportSceneQualityTest`: contraste do Hoodie, painel chapado, azul do Hoodie, paleta, escala 1:1 e banco
  no slot; exporta silhuetas.
- `TransportSceneV3ExportTest` (`-PtransportReview=true`) exporta a revisão em `docs/transport-art/review/`.

## Regra de aprovação
- **A inspeção do agente não conta como aprovação.** Só revisão humana marca `manualReview=true` em
  `assets-source/scenes/transport/scene-art-status.json`; o teste `everySceneInTheAppHasAHumanReview` exige isso de
  todas as cenas que estão no app.
- Goldens das cenas só são regravados depois de revisão humana.

## Estado (07/10/2026)
- As quatro cenas foram aprovadas no chat e são as **únicas** cenas de carro, trem, metrô e ônibus do app:
  as cenas antigas (`CarScene`, `BusSceneV2`/`BusSeatProp`, o interior antigo de ônibus/trem/metrô), as flags
  `BUS_SCENE_V2`, `TRAIN_SCENE_V2` e `TRANSPORT_SCENES_V3`, os passageiros antigos desses interiores no
  `NpcDirector` e as chaves de versão do Pixel Lab foram removidos. Bicicleta, "outro transporte" e transporte
  genérico continuam como estavam (não têm versão em camadas).
- O Hoodie dirigindo (`assets-source/hoodie/hoodie_transport.aseprite`, CAR_* de lado) foi aprovado no chat e entrou
  no APK como o grupo `transport` (`ArtBootstrapStudio.GROUPS`, `RequiredShippedAnimations`, `art-status.json`, CI).
- Goldens regravados com as cenas aprovadas: `transport-scenes-v1.sha256` e `car-scenes-v1.sha256`.
- **Carro v2 (09/10/2026):** o interior (`vehicle_back`) passava do contorno da carroceria (teto, capô e coluna A) e
  aparecia sobre o fundo; agora é recortado pelas janelas (`Car.FRONT_WINDOW`/`REAR_WINDOW`, a mesma geometria que abre os
  vãos da carroceria) e o banco de trás foi retirado. Nova trava `interiorNeverLeaksOutsideTheWindows` (flood fill do
  exterior da frente × `vehicle_back`). Trem, metrô e ônibus são interiores de quadro cheio (a frente toca a borda da
  tela) e a trava não se aplica a eles. Goldens do carro regravados (12 hashes + `car_day`/`car_night` em
  `transport-scenes-v1.sha256`) e imagens `docs/transport-art/review/car-*.png` atualizadas.
