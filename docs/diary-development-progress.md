# Diário Visual — execução do plano

Plano recebido: [diary-development-plan.md](diary-development-plan.md).

## Fase 1 — Base

Existem modelos em `domain/diary`, `LoadDiaryUseCase`, `DiaryRepository` e os cinco builders em `engine/diary`. O repositório consulta contextos e atividades sobrepostos ao dia, timeline e lugares; montagem ocorre em Default, leitura em IO. Há testes dedicados a resumo, timeline, visitas, mapa, replay e repositório.

## Fase 2 — Tela

Rota e aba Diário existentes. Hoje, Ontem e calendário, resumo, timeline, mapa e detalhes por lugar implementados. Adicionada nova tentativa em falhas de leitura e limpeza de seleção ao trocar data.

## Fase 3 — Pixel art

Renderer, terreno, prédios, caminhos, iluminação, marcador, cache estático e áreas de toque existem em `pixel/diary`. A cidade agrupa visitas do mesmo lugar em um prédio; cada visita e transição mantém identidade própria para replay e detalhes, opção prevista no plano.

## Fase 4 — Replay

Play, pause, reset, velocidades, relógio e HUD sincronizados existem. Reset agora conserva a velocidade. Timeline possui lista de altura limitada, destaque acessível e rolagem automática para o evento atual, sem deslocar toda a tela.

## Fase 5 — Polimento

Estados de carregamento e vazio, aviso de reconstrução simbólica sem GPS e Diary Lab existentes. Adicionado cache de até sete dias anteriores na sessão da tela; hoje é consultado novamente. Cancelamento da camada digital propaga corretamente. Seletor de datas possui estado selecionado, papel de botão e altura mínima de 48 dp; reset tem descrição acessível.

## Verificação

Polimento adicional: a timeline fornece ao leitor de tela o ator (Você, Hoodie ou Sistema), horário, título e subtítulo. Adicionados testes Compose da seleção Hoje/Ontem e de todas as velocidades. Execução em 02/10/2026: **cinco testes Compose aprovados**, zero falhas, no Pixel 8 / Android 17; compilação debug aprovada. A suíte também cobre retomar, reiniciar, rever e rolar a timeline até o evento do replay.

Revisão manual em 02/10/2026: onboarding concluído sem localização; Hoje abriu com resumo, cidade e controles; Ontem abriu estado vazio. Toque no prédio Casa abriu detalhes (hierarquia acessível confirmou duração, visita, horários e eventos). O backend OpenGL do AVD Android 17 apresentou SIGFPE em libhwui; Vulkan permitiu abrir o app, mas a captura de tela não refletiu a camada modal, embora a hierarquia a confirmasse. Revisão visual do modal em outro backend/dispositivo continua pendente. Arquivos locais de revisão: `app/build/diary-review.png`, `app/build/diary-yesterday-review.png`.

Testes de lugares e consentimento digital passaram na execução anterior. Primeira execução do Diário: **66 testes, 19 classes, zero falhas**. Segunda execução: **69 testes unitários, zero falhas**, incluindo dia vazio, contexto aberto/futuro e lugar desconhecido. **Três testes Compose aprovados no Pixel 8 / Android 17**, cobrindo controles e rolagem. Os dois seletores iniciais foram corrigidos para os rótulos em maiúsculas exibidos pelo PixelButton. Alvos de toque do mapa ampliados para no mínimo 48 dp. Imagem `app/build/pixel-preview/diary_map.png` inspecionada: quatro cenários com prédios, caminhos, marcador e iluminação legíveis. Revisão visual da tela completa permanece pendente. Não considerar o plano integralmente concluído antes dessas verificações.
