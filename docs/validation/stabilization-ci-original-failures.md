# Diagnóstico das nove falhas originais

## Evidência consultada

Execução [Android CI #46](https://github.com/rafaelvalquer/Hoodie/actions/runs/38326110415),
disparada por push em `f4aab7baee04407a7b34e219f473af18bdc00c0c` em 13/10/2026.
Job unitário `115065044558`: **885 testes, nove falhas, seis ignorados**.
Lint aprovado; assemble e instrumentation ignorados devido à dependência do
job unitário. Não foram falhas desses dois jobs.

Foram lidos o log original do job e os HTMLs do artefato `unit-test-reports`
(`11780858214`). SHA-256 do ZIP baixado:
`ddfc82116eaf69f4f1f099e0d8f3d9767e89583e8136c72983c853fd87b71e58`.
O diagnóstico abaixo usa as asserções desses HTMLs e o código da versão-base,
além das regressões atuais. A consulta anterior filtrava somente eventos de
pull request e não encontrava essa execução, que é de push.

## Classificação individual

| Teste | Falha original | Classificação e tratamento |
| --- | --- | --- |
| `MobilityRulesTest` — segurança | Esperava `ASK`, recebeu `DEFER` na linha 118. | **Expectativa desatualizada após mudança intencional.** App aberto não autoriza interação enquanto o veículo pode estar em movimento. A expectativa atual exige adiamento. |
| `MobilityEngineIntegrationTest` — viagem completa | `assertNotNull` da pergunta de transporte na linha 76, imediatamente após abrir o app com veículo em movimento. | **Expectativa desatualizada após mudança intencional**, seguida de validação funcional do fluxo. A pergunta só é respondida depois de `STILL`; a caminhada final exige continuidade e reconciliação. Mantidas as verificações de sessões, cenas e Diário. |
| `MobilityEngineIntegrationTest` — bateria | A asserção “checagem agendada só por tempo, nunca periódica de GPS” falhou na linha 190. | **Expectativa desatualizada após mudança intencional.** Uma checagem de caminhada em 45 s é necessária e não equivale a GPS contínuo. O teste atual exige essa checagem e continua exigindo zero leituras de posição no percurso com geofences. As leituras pontuais de velocidade têm testes próprios de autorização, cancelamento e orçamento. |
| `MobilityEngineIntegrationTest` — veículo para caminhada | Esperava `[WALKING, VEHICLE_UNKNOWN, WALKING]`, recebeu `[WALKING, VEHICLE_UNKNOWN]`, linha 131. | **Defeito de implementação da reconciliação pendente**, com expectativa temporal a adequar à histerese. Uma observação coloca a caminhada em avaliação; `onCheck()` confirma após 45 s de evidência sustentada, inclusive depois de reiniciar a engine. Sem segundo ENTER e sem duplicação. |
| `MobilityLearningIntegrationTest` | Esperava `[WALKING, BUS, WALKING]`, recebeu `[WALKING, BUS]`, linha 71. | **Defeito de implementação**, decorrente da mesma caminhada pendente não resolvida. O trajeto aprendido precisa preservar o trecho final a pé. A suíte atual mantém a expectativa multimodal e as verificações de redução de perguntas e chegada automática. |
| `TransportIntelligenceIntegrationTest` | Esperava `[WALKING, BUS, WALKING]`, recebeu `[WALKING, BUS]`, linha 47. | **Expectativa desatualizada para uma correção explícita.** Este cenário grava BUS com `USER_CORRECTION`; a política de prioridade preserva essa correção até a chegada. O teste atual exige `[WALKING, BUS]` e preservação da origem da decisão. Caminhada após veículo inferido, aprendido ou preferido é coberta separadamente; não se generaliza esta exceção para todos os veículos. |
| `FullDayIntelligenceIntegrationTest` | Esperava `PROBABLE`, recebeu `IDENTIFIED`, asserção na linha 170 do corpo suspenso. | **Expectativa desatualizada após mudança intencional.** O contexto usa geofence e confiança `0,96`; `HomeNowAssembler` define `IDENTIFIED` a partir de `0,90`. Confirmação explícita continua distinta de identificação automática. |
| `NewPlaceScenesTest` | `shopping_look`: esperado `a1b1c342…4d0c6`, obtido `9eba9a94…f982`. | **Referência visual que precisava de revisão.** A aparência do Mercado foi aprovada explicitamente pelo usuário em 13/10/2026, antes de atualizar os quatro hashes `shopping_*`. A aprovação posterior do Passeio está registrada separadamente. |
| `NpcArtV3GoldenTest` | `scene_leisure`: esperado `b800b0df…5fd6`, obtido `20bda550…7046`. | **Referência visual que precisava de revisão.** A falha original já era do Passeio. A integração posterior mudou novamente a cena. O usuário aprovou a matriz completa e as cenas atuais; manifestos e hash atual foram atualizados pelo gate, e a comparação passou com `approveNpcV3Goldens=false`. |

Nenhuma das nove falhas originais foi classificada como problema de ambiente.
A falha local posterior de transformação ASM por arquivo intermediário ausente
foi um problema de execução diferente, registrado no acompanhamento da sprint.

## Evidência atual

A suíte completa atual, incluindo o dia integrado e os testes remotos do Passeio,
executou **1.000 casos, zero falhas e seis ignorados**, incluindo todas as classes
acima. Resultados finais estão em `stabilization-0.1.8.16.md` e devem ser
referenciados na descrição do PR.
