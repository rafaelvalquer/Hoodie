# Hoodie — efeitos suaves de interação

Prévia de sons originais, produzidos por síntese digital, com timbre retrô suave.
Não contém gravações de animais, vozes ou samples de terceiros.

| Arquivo | Duração | Uso proposto |
| --- | --- | --- |
| `hoodie_tap.wav` | 180 ms | Reação iniciada por toque rápido |
| `hoodie_pet_head.wav` | 600 ms | Início do carinho, PET_HEAD |
| `hoodie_purr.wav` | 1.200 ms | Ronronar estilizado, PURR |
| `hoodie_nuzzle.wav` | 700 ms | Finalização afetuosa, NUZZLE |
| `hoodie_petting_full.wav` | 2.500 ms | Sequência completa PET_HEAD → PURR → NUZZLE |
| `hoodie_interactions_preview.wav` | 3.230 ms | Toque, pausa de 550 ms e carinho completo |

Formato: WAV PCM, mono, 16 bits, 44,1 kHz. Ataques e finais suavizados para evitar
estalos; volume moderado, sem normalização agressiva. O ronronar é propositalmente
sintético. As frequências dos sinais melódicos pertencem ao acorde de Lá maior.

Regerar de forma determinística com `python generate_interaction_sfx.py`.

São arquivos de prévia: ainda não foram incorporados aos recursos ou à reprodução
do aplicativo. Na integração, sincronizar com o início real da animação e cancelar
na troca de cena/saída da Home; gestos bloqueados ou expirados permanecem silenciosos.
Usar a sequência completa somente quando as três etapas forem executadas, para
que sequências curtas não reproduzam sons de animações ausentes.
