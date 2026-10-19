# Assets e ficha da loja

Estado: inventário técnico inicial, **sem aprovação final de direitos ou screenshots da loja**.

| Material | Fonte no projeto | Evidência / pendência |
|---|---|---|
| Press Start 2P | `app/src/main/res/font/hoodie_pixel.ttf` | Licença SIL OFL em `app/src/main/assets/licenses/press_start_2p_ofl.txt`; revisar atribuição e distribuição da fonte |
| Sprites, personagens e cenários | `app/src/main/assets/pixel/` e renderizadores Kotlin | Revisar autoria/proveniência e autorização de uso; a existência no Git não comprova direitos |
| Sons de interação (7 WAVs) | `app/src/main/res/raw/hoodie_*.wav` | Proveniência técnica conferida: síntese original pelo gerador v2, sem samples externos; sete arquivos regenerados idênticos byte a byte. [Hashes e metadados](sound-provenance.json). Arquivar a confirmação de titularidade junto à aprovação final dos assets |
| Ícone | Recursos mipmap/drawable do app | Conferir versão final e direitos |
| Tiles de mapa | OpenStreetMap via osmdroid | Manter atribuição visível e políticas dos provedores; não remover crédito das capturas |
| Screenshots da loja | Ainda não produzidas/aprovadas | Capturar variante final com dados fictícios; incluir Home, Diário, lugares, permissões e backup somente se liberado |

As referências de regressão visual não são automaticamente material de marketing. Para cada asset externo, arquivar autor, URL de origem, licença/versão, atribuição e restrições. Não publicar uma declaração geral de propriedade sem revisar esse inventário.

Os efeitos suaves retrô são produzidos por `assets-source/hoodie/audio/v2/generate_interaction_sfx_v2.py`, usando somente math, random, struct, wave e pathlib. Na conferência de 19/10/2026, o gerador foi executado com saída redirecionada para `app/build/sfx-provenance-check/`; os hashes dos sete recursos entregues coincidiram com os arquivos-fonte v2 e com os regenerados. Formato confirmado: PCM mono, 16 bits, 44,1 kHz. Nenhum WAV do app foi alterado nessa conferência. Esse resultado resolve a origem técnica dos sons; não é uma aprovação geral de direitos dos sprites, fontes, ícones ou material da loja.

Ficha: nome Hoodie; contato rafael.valquer@gmail.com; política no link documentado em `play-compliance.md`. Categoria, classificação indicativa, público-alvo, países, anúncios e textos definitivos precisam ser revisados e preenchidos na Console.
