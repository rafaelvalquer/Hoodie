# Hoodie — efeitos de interação v2 (proposta)

Mesma ideia da v1 (síntese original, timbre retrô suave, acorde de Lá maior, sem samples), com
quatro correções medidas:

| | v1 | v2 |
|---|---|---|
| Ronronar no alto-falante do celular | -55 dBFS (praticamente some) | -27 dBFS |
| Volume entre os sons | variação de 7 dB | casado em -24 dBFS no celular |
| Timbre | senoide quase pura | quadrada suave, deslize de afinação no ataque, vibrato leve |
| Toques repetidos | 1 arquivo | 3 toques e 2 carinhos |

- **Ronronar**: harmônicos na faixa de 250–900 Hz, que o alto-falante do celular reproduz;
  o ouvido reconstrói o grave. Inspiração e expiração alternam ritmo e intensidade.
- **Carinho e finalização**: textura de ruído bem suave, como mão no pelo; a finalização termina
  com um "boop" curto subindo.
- **Toque**: "pop" com deslize para baixo e um clique de 12 ms.

| Arquivo | Uso proposto |
| --- | --- |
| `hoodie_tap_1..3.wav` | Toque rápido (sortear um; nunca o mesmo duas vezes seguidas) |
| `hoodie_pet_head_1..2.wav` | Início do carinho, PET_HEAD |
| `hoodie_purr.wav` | Ronronar, PURR |
| `hoodie_nuzzle.wav` | Finalização, NUZZLE |
| `hoodie_petting_full.wav` | Sequência completa, com transição suave entre as etapas |
| `hoodie_interactions_preview_v2.wav` | Os três toques e o carinho completo |
| `comparacao_v1_v2.wav` | A/B: toque, ronronar e carinho completo — v1 e logo depois v2 |

WAV PCM mono, 16 bits, 44,1 kHz. Regerar: `python generate_interaction_sfx_v2.py` (o script
verifica pico e bordas sem estalo). Ouça também no alto-falante do celular, não só no fone.

## No app

Aprovados e integrados: os arquivos `hoodie_tap_1..3`, `hoodie_pet_head_1..2`, `hoodie_purr` e `hoodie_nuzzle` foram
copiados para `app/src/main/res/raw` (mesmos nomes). Ao regerar, copie de novo. Detalhes da reprodução em
`docs/hoodie-interactions-v1.md` (seção Sons).
