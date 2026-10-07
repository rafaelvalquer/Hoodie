# Cenas de transporte V3 — andamento

Regras de arte: [transport-art-bible.md](transport-art-bible.md). Escolha de enquadramento (07/10/2026):
Carro B, Trem A, Metrô A. Ônibus (incluído depois): par de bancos de frente, janela atrás, encosto da fileira da
frente e barra no primeiro plano — enquadramento do plano, sem rodada de thumbnails.

| Cena | Estado | Fonte | Revisão humana |
|---|---|---|---|
| Carro | rascunho (bootstrap) | `assets-source/scenes/transport/car.aseprite` | pendente |
| Trem | rascunho (bootstrap) | `assets-source/scenes/transport/train.aseprite` | pendente |
| Metrô | rascunho (bootstrap) | `assets-source/scenes/transport/metro.aseprite` | pendente |
| Ônibus | rascunho (bootstrap) | `assets-source/scenes/transport/bus.aseprite` | pendente |

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
- **A inspeção do agente não conta como aprovação.** Só o Rafael marca `manualReview=true` em
  `assets-source/scenes/transport/scene-art-status.json`.
- `HoodieConfig.TRANSPORT_SCENES_V3` só pode ser ligado com todas as cenas revisadas (teste
  `v3StaysOffUntilAHumanApproves`); a release também exige revisão. Até lá, a V3 aparece só no Pixel Lab.
- Goldens das cenas V3 só são gravados depois da revisão humana.

## Hoodie
- `assets-source/hoodie/hoodie_transport.aseprite` (CAR_* de lado) é **só fonte** para o Rafael pintar
  (`ArtBootstrapStudio.PENDING_GROUPS`). Fica fora do APK e dos scripts de exportação até a aprovação: se entrasse
  agora, a sheet substituiria o procedural também no carro atual.
