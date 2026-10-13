# Mobilidade confiável e rotina efetiva — implementação

## Dados e privacidade

Room 11 acrescenta `transport_evidence`, `effective_routine_versions` e os campos
`evidenceId` e `confirmedAt` dos trechos. A migração 10 → 11 mantém os dados
anteriores. Velocidades históricas da sessão não são associadas artificialmente a
um trecho. Os campos antigos continuam disponíveis para compatibilidade.

Cada período contínuo em veículo tem seu próprio ID, agregados de velocidade e
paradas, orçamento de cinco tentativas e intervalo de 60 segundos. Mudanças de
classificação e STILL preservam esse período; uma caminhada confirmada o encerra.
Nenhuma coordenada, trajeto ou amostra individual é adicionada a essas tabelas.

## Execução e transições

`TransportSpeedSampleWorker` aguarda a leitura suspensa. O scheduler usa trabalho
único por evidência com KEEP e tags para cancelar por sessão ou globalmente.
Reservas e aplicação são transacionais; os IDs consumidos impedem a reexecução do
mesmo WorkRequest. Cada reserva é revalidada após a leitura e tem validade de
120 segundos. A leitura tem timeout de 20 segundos; cancelamento chega ao token
do Play Services. Permissão em uso exige visibilidade do processo inteiro.

A engine apenas enfileira velocidade. A leitura de chegada também ocorre fora
do mutex, e o resultado só é utilizado se a sessão/parada ainda forem as mesmas.
Desligar a detecção, apagar histórico e finalizar a sessão cancelam trabalhos e
invalidam resultados pendentes.

A caminhada abre em `pendingMovementAt`, após 45 segundos de continuidade.
`confirmedAt` registra o instante da decisão. Fechamento, abertura e limpeza da
pendência pertencem à mesma transação. Chegada resolve uma pendência elegível
antes de fechar a viagem. A proteção de trechos corrigidos continua em vigor.

## Rotina compartilhada

O resolver puro recebe referências manuais, modos, padrões e exceção do dia.
Confiança mínima: 0,60; validade: 28 dias; amostras mínimas: três por grupo,
quatro para dia útil específico; dispersão máxima: 45 minutos. Cada padrão pode
deslocar seu horário manual em até 90 minutos, com a limitação indicada na Home.
Pares incompatíveis retornam aos horários manuais, incluindo almoço mínimo de
15 minutos e duração de sono entre quatro e doze horas. Sono pode atravessar
meia-noite. Saída, academia e retorno são somente previsões.

A tela de Rotina preserva os horários manuais e permite escolher Manual ou
Automático para cada evento. A Home mostra a origem do próximo horário, confiança
e amostras quando aprendido. As engines de contexto, mobilidade, estado do dia e
Hoodie usam o mesmo repositório.

Versões diárias registram vigência, proveniência e referências manuais. Aprendizado
novo prepara o dia seguinte; salvamentos manuais entram em vigor no salvamento.
Consultas históricas e simulação utilizam as versões vigentes, com baseline manual
para períodos sem versão. Previsões e atividades simuladas não alimentam o learner.

## Validação automatizada

- `VehicleEvidenceTest`: períodos multimodais, reservas, throttle, cancelamento,
  desligamento e permissão em uso.
- `MobilityStabilityRegressionTest`: reinício, caminhada sem segundo ENTER e leitura
  suspensa sem bloquear eventos.
- `EffectiveRoutineResolverTest`: precedência, elegibilidade, grupos, pares e sono.
- `EffectiveRoutineRepositoryTest`: aprendizado no próximo dia, histórico, exceções
  e Flow após salvamento.
- `SpeedWorkManagerTest`: KEEP, cancelamento por identidade e retry do banco.
- `Migration7To8Test`: caminho completo 1–10 → 11 e preservação do histórico v10.
- `SimulatorPersistenceTest`: equivalência entre avanço único e checagens
  intermediárias, incluindo mudanças da rotina durante o dia.

## Resultados em 12/10/2026

- Rodada funcional final: **452 testes aprovados**, sem falhas ou testes ignorados,
  incluindo integrações Room, WorkManager, migrações, rotina, mobilidade,
  simulação, documentação e compatibilidade dos DAOs simulados.
- Pixel 8 (`emulator-5554`): **14 testes instrumentados aprovados**. Cobrem
  suspensão/retomada e recriação da atividade, estabilidade da rotina,
  permissões, agendamento, inicialização, banco cifrado, preservação de viagens,
  correções e configuração na migração 10 → 11, além dos caminhos 1–10 → 11.
- Build dos APKs e `lintDebug` concluídos com **BUILD SUCCESSFUL**. APKs de
  aplicativo e testes instalados com sucesso.
- A suíte ampliada executou 917 testes, com seis ignorados e duas falhas:
  contagem de tabelas do README (corrigida e verificada na rodada final) e
  golden visual `NewPlaceScenesTest.shopping_look`. O renderer, o teste visual
  e sua referência não foram alterados nesta entrega; a divergência visual
  permanece pendente e a referência não foi regenerada para ocultá-la.
- O Pixel 8 estava sem permissões de localização. As regras de permissão em uso,
  descarte e cancelamento foram testadas com fontes controladas; deslocamento
  físico e recepção real de GPS não foram validados.

Referências: [CoroutineWorker](https://developer.android.com/reference/androidx/work/CoroutineWorker)
e [await com CancellationTokenSource](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/kotlinx.coroutines.tasks/await.html).
