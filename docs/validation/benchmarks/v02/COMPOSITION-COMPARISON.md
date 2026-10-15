# Composição reutilizada — comparação JVM

Gate aprovado: 7 testes JVM, debug e APK instrumentado (`composition-worker/gradle.log`). **Os 99 checksums coincidem com a referência**, incluindo dia/noite, objetos, histórias, saída/retorno e toque. Testes adicionais verificam que entradas são reutilizadas, todos os atores compartilham um snapshot por timestamp e renderers concorrentes preservam pixels.

| Cena | Bytes/quadro antes | Bytes/quadro depois | Redução |
|---|---:|---:|---:|
| OFFICE | 14159 | 14135 | 0.2% |
| RESTAURANT | 42250 | 39886 | 5.6% |
| SHOPPING | 17253 | 15038 | 12.8% |
| STORE | 11055 | 8618 | 22.0% |
| LEISURE | 29670 | 27248 | 8.2% |
| BAKERY | 41900 | 39185 | 6.5% |
| MALL | 56310 | 53917 | 4.3% |
| BEACH | 28987 | 26608 | 8.2% |
| CINEMA | 26031 | 23459 | 9.9% |
| PHARMACY | 36356 | 34180 | 6.0% |
| HOTEL | 57270 | 54659 | 4.6% |

Os números incluem frame/modelo/checksum e variam com JIT/GC; não significam que toda alocação restante vem da composição. O Escritório já tinha um plano reutilizado. O caminho worker do Canvas foi compilado, mas ainda precisa da comparação Android posterior. Nesta rodada, a política de sessão noturna ainda é a original; o reuso através de mudanças de iluminação será validado separadamente, pois preserva atores que antes desapareciam na transição.
