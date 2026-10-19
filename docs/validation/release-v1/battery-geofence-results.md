# RELEASE-06 — campanha física de sete dias

Estado: **não iniciada**. Em 19/10/2026 o responsável informou possuir aparelhos físicos para HML e depois esclareceu que nenhum está disponível para conectar neste momento. Modelos e versões Android pendentes de identificação. Nenhum resultado simulado equivale a sete dias reais.

Reutilizar `GEOFENCE_SCENARIOS.md`, Developer Lab e os motores atuais. Não adicionar localização contínua.

Matriz obrigatória: Samsung intermediário, Motorola intermediário, Pixel/AOSP. Incluir Android mais antigo suportado quando disponível. Registrar modelo, API, fabricante, build/SHA, permissões e modo de bateria.

Comparar períodos A (monitoramento desligado) e B (ligado) com condições equivalentes. Para cada período coletar `adb shell dumpsys batterystats <pacote>`, `adb shell dumpsys jobscheduler`, `adb shell dumpsys activity processes`, diagnóstico sanitizado, Perfetto quando necessário e Android vitals quando disponível. Não publicar dumps sem revisar dados pessoais.

| Dia/período | Dispositivo | A/B | Bateria/background/wakeups/workers/temperatura/memória | Jornada/ANR/crash | Evidência |
|---|---|---|---|---|---|
| Pendente | Pendente | Pendente | Pendente | Pendente | Pendente |

Cobrir Casa → Trabalho → Restaurante → Trabalho → Casa, noite em Casa, app fechado por horas, reboot, Wi-Fi/dados, GPS oscilando, permissão negada, background revogado, economia de bateria, fuso/horário e retorno após inatividade. Conferir ausência de visitas duplicadas e intervalos impossíveis.

Definir limites de consumo após a linha de base; registrar a decisão. Se consumo alto, investigar registros redundantes de geofences, concorrência de workers, loops e frequência de reconciliação. Tratar atrasos do SO sem prometer detecção instantânea. Separar force-stop de morte de processo/remoção dos recentes.

Relatório compartilhável não deve incluir coordenadas, endereços, mensagens ou histórico detalhado. Nenhum SDK de analytics comportamental faz parte desta entrega.
