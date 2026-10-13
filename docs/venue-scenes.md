# Estabelecimentos — Venue Activity Engine

Seis cenas novas escolhidas pelo **tipo do lugar** (não por novos contextos):

| PlaceType | Contexto(s) | Cena | Flag | Sem a flag |
|---|---|---|---|---|
| `BAKERY` | DINING, LUNCH | `BakeryScene` | `VENUE_BAKERY` | Restaurante |
| `MALL` | SHOPPING, LEISURE | `MallScene` | `VENUE_MALL` | Compras |
| `BEACH` | LEISURE | `BeachScene` | `VENUE_BEACH` | Passeio |
| `CINEMA` | LEISURE | `CinemaScene` | `VENUE_CINEMA` | Passeio |
| `PHARMACY` | SHOPPING | `PharmacyScene` | `VENUE_PHARMACY` | Compras |
| `HOTEL` | TRAVEL | `HotelScene` | `VENUE_HOTEL` | Exterior genérico |

- Os tipos novos ficam no **fim** do enum (nomes persistidos); na interface "Outro" segue por último.
- O tipo só fica guardado no evento de contexto onde ele escolhe uma cena (`PlaceType.venueFor`).
- `VisualDirector.venueScene` valida contexto × tipo; um deslocamento real tem prioridade; "Outro" nunca
  escolhe uma cena nova.
- **As flags estão desligadas**: cada cena aparece sempre no Pixel Lab (galeria e seletor de cena) para
  validação nos quatro períodos; depois da aprovação, basta ligar a flag em `HoodieConfig`.

## Motor compartilhado (`pixel/npc/venue`)

- `VenueNpcSession` — simulação determinística por `VenueSessionKey(cena, lugar, dia, variante, noite)`:
  todos os NPCs numa linha do tempo conjunta (andar, virar, parar), o mais atrasado decide primeiro; mesma
  chave + mesmo instante = mesma cena em qualquer taxa de quadros e no replay do Diário. Histórico podado.
- `VenueNavigationGraph` — pontos (`VenueSpot`) e trechos que nunca atravessam obstáculos (checado no init).
- `VenueSpotReservation` — um ocupante por ponto; quem para num ponto o reserva de fato.
- `VenueInteractionCoordinator` — pedidos de balcão (um por vez; só retira depois de pronto), encontros
  (um por vez, cooldown 60–100 s), um balão de fala por vez, eventos de objetos (`VenueEvent`), voos (bola)
  e intervalos (porta do elevador).
- `VenueNpcBrain` / `VenueNpcProfile` — escolha ponderada com restrições, cooldown e no máximo 2 repetições.
- `VenueScripts` — entrar, fila/pedido, retirar e atender, reaproveitados por todas as cenas.
- Cada cena tem o seu roteiro (`*Venue.kt`) e o seu estado de objetos (`*Ambient`), lido pelos props.

## Cenas

- **Padaria**: barista atrás do balcão (anota → máquina → entrega → limpa só sem pedido à vista), consumidor
  ou leitor (fila, pedido, retira a xícara pronta, senta, devolve) e compra rápida (vitrine → sacola → paga).
- **Farmácia**: atendente que atende e repõe prateleiras quando ninguém vai precisar do caixa; clientes que
  olham, levam uma caixinha genérica e pagam. Nada identifica remédio, sintoma ou motivo (teste garante).
- **Praia**: dupla da bola com dono lógico (só quem está com ela arremessa; quem recebe agarra na chegada),
  turista, banhista e sorveteiro; ondas, espuma, nuvens, gaivotas e guarda-sol cíclicos.
- **Shopping**: corredor com vitrines, escada rolante animada, banco; comprador entra na loja e volta com
  sacola; amigos se encontram; `MallCrowdCoordinator` limita o público e as entradas.
- **Cinema**: saguão e sala no mesmo quadro (a sala vista da tela); `CinemaLightingController` com
  LOBBY → DIMMING → SCREENING → EXIT_LIGHTS (rampas, luz mínima legível); poltronas reservadas por sessão,
  atrasado que procura o lugar, reações com atrasos próprios.
- **Hotel**: recepção (computador, quadro de chaves, cartão), `LuggageAttachment` (mala na mão / no chão no
  check-in), `ElevatorController` (CLOSED → OPENING → OPEN → CLOSING, nunca fecha com alguém na soleira).

O Hoodie tem animações próprias em `animation/definitions/*Animations.kt` (19 novas) e `approachSpot` encadeia
lugares (pedir no balcão → sentar; pipoca → poltrona; check-in → sofá).

## Testes (`app/src/test/.../pixel/venue`)

Roteamento, determinismo, reservas, caminhos (sem atravessar, sem teleporte, viradas animadas, sem ciclos),
interações, objetos, replay, desempenho e folhas de revisão (`build/pixel-preview/venues/`). Os goldens
(`venue-goldens-v1.sha256`) só são gravados com `RECORD_VENUE_GOLDENS=true` depois da aprovação das folhas.
