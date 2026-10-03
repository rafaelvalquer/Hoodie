# Alteração externa do checkout — 02/10/2026

O trabalho da rodada anterior produziu 52 testes JVM sem falhas e 34 casos Android aprovados, com um caso de teclado ignorado. Isso ocorreu com schema Room v7 e fonte pixel integrada. Esses resultados não validam o checkout atual.

## Estado observado após 18:05

- HEAD: `8132b1c`, main, merge de Novo Local e mobilidade.
- HoodieDatabase novamente declara versão 6.
- Migration6To7.kt, hoodie_pixel.ttf e ScreenGoldenMatrixTest.kt não existem nos caminhos de produção/teste.
- Home, Digital, Diário, Onboarding e Ajustes perderam suas funções Content; o relógio PixelRenderFrame também desapareceu.
- PlacePicker contém as novas regras de foco e altura do mapa; voltou a expor erros String e evento ShowMessage/Toast.
- MobilityEngine contém onManualContext, ausente na compilação principal que antecedeu a última compilação dos testes.

Não atribuir a substituição a um usuário ou processo específico sem evidência. Foi solicitada uma clarificação sobre a intenção dessa restauração, para integrar o objetivo completo sem contrariar uma decisão humana no projeto compartilhado.

## Preservação

As mudanças locais posteriores no PlacePicker e nos testes foram salvas em `app/build/stability-recovery/typed-picker-and-tests.patch`. Os scripts de extração, relógio visual, fonte, dados de screenshot e migração continuam disponíveis em app/build. A fonte original e sua licença também permanecem ali. Nenhuma referência visual foi aprovada ou registrada até aqui.

A última rodada terminou em erro de compilação dos testes, antes da captura piloto. O objetivo integral continua aberto; reconciliar o estado atual e repetir compilação, testes e capturas antes de considerar qualquer etapa posterior aprovada.

## Autorização e reintegração

O usuário respondeu: “Continue e reintegre o plano completo”. A recuperação foi aplicada sobre a main atual, preservando as regras de foco e altura do mapa do seletor e a mobilidade. Foram restaurados fonte/licença, migração v7, erros tipados, host de geofence, textos, acessibilidade, funções Content, relógio visual determinístico, mapa offline de captura e matriz visual. A compilação atual está em andamento; os resultados anteriores continuam históricos até nova execução.
