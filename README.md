# HOODIE — Seu companheiro de rotina

```
      /\_/\
     ( o.o )   Você vive a sua rotina.
      > ^ <    O Hoodie vive uma rotina paralela.
```

MVP Android **100% nativo (Kotlin + Jetpack Compose)**, **offline** e com **todo o histórico só no aparelho**.
O app nem declara a permissão `INTERNET`.

## Como rodar

Requisitos: JDK 17+ (testado com JDK 21) e Android SDK 36.

```bash
./gradlew :app:assembleDebug        # APK em app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # testes de regra (CT-*), persistência e assets
```

Revisão visual sem emulador: os testes `SpritePreviewTest` e `ScenePreviewTest` exportam PNGs para
`app/build/pixel-preview/` (model sheet do Hoodie e o *vertical slice* casa → rua → trabalho → almoço → casa).
O ícone do app é gerado a partir do próprio sprite: `./gradlew :app:testDebugUnitTest --tests '*IconExportTest*' -PexportIcons=true`.

## Arquitetura

```
 Sensores (geofence, hora, rotina, confirmações)
            │
            ▼
   ContextEngine ──► context_events (fonte única: "onde a pessoa provavelmente está")
            │
            ▼
   HoodieEngine ──► HoodieSimulator (puro) ──► hoodie_state / hoodie_activities / timeline
            │
            ▼
   VisualDirector ──► AnimationStateMachine ──► SceneRenderer (240×320, escala inteira, sem filtro)
            │
            ▼
            🐱
```

| Camada | Pacote | Papel |
|---|---|---|
| Modelo/tempo | `core.model`, `core.time` | `UserContextType`, `HoodieActivity`, `ClockProvider`, `DayPeriod`, janelas de horário |
| Persistência | `core.database`, `core.datastore` | Room (11 tabelas) + DataStore |
| Segurança | `core.security` | Coordenadas cifradas (AES‑256‑GCM, chave no Android Keystore) |
| Sensores | `core.location`, `core.geofence`, `receiver` | Geofences ENTER/EXIT/DWELL, leitura pontual, reboot |
| Regras puras | `engine.context.ContextScorer`, `ConfirmationPolicy`, `engine.routine`, `engine.hoodie.HoodieDecisionEngine`, `NeedsEngine`, `HoodieSimulator` | Sem Android: 100% testáveis |
| Orquestração | `engine.context.ContextEngine`, `engine.hoodie.HoodieEngine`, `engine.memory`, `engine.dialogue` | Aplica regras ao banco, perguntas, notificações |
| Background | `worker` | Reconciliação a cada 15 min, checagem de almoço (+15 min) e de deslocamento longo (+40 min) |
| Pixel engine | `pixel.*` | Sprite procedural, 31 animações, 9 cenas, iluminação, partículas, transições |
| UI | `presentation.*` | MVVM com Hilt, Navigation Compose, Material 3 |

### O princípio mais importante

Nada roda "contando o tempo". O estado do gato é `startedAt` + `expectedEndAt` + necessidades em `needsAt`.
Ao abrir o app (ou quando o worker roda), `HoodieSimulator.advance()` **reconstrói** tudo o que aconteceu no
intervalo, usando seeds derivadas do próprio instante. Abrir às 10:30 ou ter o worker rodando a cada 15 minutos
produz **exatamente o mesmo estado** (teste `CT-PERSIST`).

### Context Engine (sem IA)

Confiança determinística: lugar conhecido +40, horário esperado +30, dia esperado +20, histórico +10..30.
`≥ 85` aplica · `40–84` aplica e pergunta · `< 40` assume `UNKNOWN`.
Perguntas: no máximo 4 por dia e nunca o mesmo contexto em menos de 60 min. Três almoços confirmados
num horário parecido → classificação automática, sem perguntar.
GPS oscilando na borda (saída + volta em < 5 min) desfaz a saída. Sem localização: **rotina provável**.

### Motor visual

* Hoodie desenhado **por código** a partir de poses (`HoodiePose` → `HoodiePainter`), em 48×72, com contorno de 1 px
  por forma. Isso garante proporção, rosto e moletom idênticos em todos os frames (validado em teste).
* Pontos de encaixe para itens (caneca, celular, livro, controle, halteres, vassoura, panela, garrafa, garfo) e mochila.
* Cenas em camadas com ordenação por Y (o gato passa atrás da mesa, do cobertor ou do sofá), janela dinâmica
  com céu por período, relógio de parede com a hora real, microanimações (TV, monitor com código, ventilador, esteira, carros, passarinhos).
* Iluminação por overlay (manhã, dia, entardecer, noite) com áreas emissivas e halos de lâmpada em degraus.
* `AnimationStateMachine`: ENTER → LOOP → EXIT, caminhada até a porta, fade em 4 degraus, microações ponderadas
  (trabalho: digitar 45%, mouse 15%, ler 15%, café 8%…), piscadas, olhadas, orelha e 15% de chance de acenar quando você abre o app.
* **Pixel Lab** (Ajustes → 🧪 em debug, ou 5 toques na versão): cena × animação × período × expressão × velocidade,
  Animation Gallery e Scene Gallery.

## Telas do MVP

Splash · Onboarding (nome, permissões, casa, trabalho, horários, dias) · Home/Hoodie · Confirmação de contexto (card + notificação com Sim/Não) ·
Histórico (Hoje / Ontem / 7 dias, com resumo "Seu dia" e "Hoodie") · Lugares · Rotina · Memórias · Perfil do Hoodie · Ajustes · Pixel Lab.

## Privacidade

* Sem conta, servidor, Firebase, nuvem ou internet; backup em nuvem desativado (`data_extraction_rules`).
* Guardado: lugares (coordenadas cifradas), horários, contextos, histórico, memórias e estado do gato.
* **Não** guardado: trajeto GPS ou posição contínua. Eventos de geofence guardam só `lugar + transição + hora`.
* "Apagar todos os dados" limpa banco, preferências, geofences e tarefas.

## Testes (JVM)

| ID | Onde |
|---|---|
| CT-CONTEXT-001/002/003 | `ContextEngineRulesTest` |
| CT-HOODIE-001/002/003 | `HoodieEngineRulesTest` |
| CT-PERSIST (fechar/reabrir) + dia completo + horário de verão | `SimulatorPersistenceTest` |
| CT-GEO (distância/parsing) | `MiscEngineTest` |
| Assets (frames, rosto, cenas, spots, transições) | `AssetValidationTest` |

CT-GEO-001…005 em aparelho real (entrar/sair/reboot) dependem de dispositivo/emulador com rota simulada.

## Próximos passos sugeridos

1. Validar geofences em aparelho real (ou emulador com rota GPX) e calibrar raios/loitering.
2. Substituir a arte procedural por sprite sheets do Aseprite mantendo o mesmo `AnimationId`/`HoodiePose` como contrato.
3. Criptografia integral do banco (SQLCipher) — a camada já isola os dados sensíveis.
4. Pixel font própria (OFL) para títulos.
