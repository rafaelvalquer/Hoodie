# RELEASE-02 — backup em dispositivos

Estado: **pendente de homologação física**. Testes automatizados anteriores: [backup V1](../hoodie-backup-v1.md).

Disponibilidade confirmada pelo responsável em 19/10/2026: aparelhos físicos para HML. Identificar modelos e versões Android antes de preencher a matriz; disponibilidade não representa execução ou aprovação. A consulta ADB desta retomada não encontrou aparelhos conectados.

Atualização do responsável: nenhum aparelho disponível para conectar neste momento. Aguardar disponibilidade efetiva antes de iniciar a matriz física.

Build integrado: `./gradlew :app:assembleHomologacao :app:bundleHomologacao`. Instalar APK derivado do AAB para homologação de distribuição. Nunca instalar o AAB diretamente.

| Cenário | Aparelho/build/SHA | Resultado/evidência |
|---|---|---|
| Exportar com e sem histórico digital | Pendente | Pendente |
| Cancelar exportação e recriar Activity durante exportação | Pendente | Pendente |
| Documentos locais e provedor externo | Pendente | Pendente |
| Instalação limpa e substituição confirmada | Pendente | Pendente |
| Senha incorreta, arquivo inválido e truncado | Pendente | Pendente |
| Pouco espaço e falha no banco preparado | Pendente | Pendente |
| Matar processo antes/durante ativação e abrir após rollback | Pendente | Pendente |
| Importar pela recuperação de banco inacessível | Pendente | Pendente |
| Aparelho A → B, chaves Keystore distintas | Pendente | Pendente |
| Permissões e geofences reconstruídas; nova atividade sem duplicar histórico | Pendente | Pendente |
| PBKDF2 em aparelho de entrada e intermediário | Pendente | Pendente |

Em A: cadastrar Casa/Trabalho, deslocamentos, Diário, memórias e rotina; exportar. Em B: instalação limpa, importar sem a chave local de A; conferir IDs, contagens, relações e configurações, conceder permissões, reconstruir geofences e registrar nova atividade. Repetir sobre dados existentes somente em instalação de teste com cópia recuperável.

Morte normal: `adb shell am kill <pacote>` quando elegível ou mecanismo de falha controlada. `am force-stop` é cenário separado: o Android suspende eventos de background até a próxima abertura; não interpretar essa suspensão como defeito das geofences.

Preservar permanentemente os fixtures V1 em unitários/androidTest. Mudanças em `HoodieDatabase`, entidades, migrações ou DataStore exigem revisão de `BackupSchemaV1` e adapters e importação do fixture histórico.

Para produzir arquivos **sintéticos** ricos para homologação manual do APK com R8, executar o teste `com.hoodie.app.backup.BackupRoundTripIntegrationTest#thirtyDayJourneySurvivesIndependentDeviceKeysAndDigitalChoice` com `-e exportReleaseFixture true`. Após validar as relações, o teste grava dois arquivos cifrados `release-synthetic-{digital,without-digital}.hoodiebackup` no diretório externo de arquivos do host debug. Usam exclusivamente os dados fictícios do teste (4 lugares, 120 contextos/30 dias, rotina, memórias e oito datasets digitais), nome do personagem Nuvem e a senha pública de teste `hoodie recovery phrase`. As chaves locais de origem são descartadas ao terminar o teste. Copiar para Downloads de outro emulador/aparelho de teste e importar pela UI real/SAF; nunca usar essa senha em dados pessoais. Geração de candidato não equivale à homologação física exigida.
## Reexportação R8 em runtime de 16 KB

Em 19/10/2026, o APK universal derivado do AAB de homologação criou e verificou pelo SAF/Downloads um novo backup dos dados sintéticos anteriormente restaurados. O banco continuou acessível após reinício do processo e do emulador; o boot teve ANR de SystemUI recuperado, portanto essa rodada não aprova um boot limpo. Página de memória confirmada: 16384. A assinatura é de desenvolvimento, sem equivalência com distribuição pela Play.

A leitura independente autenticou todos os registros AES-GCM dos dois arquivos e comparou integralmente os registros de origem, incluindo IDs e coordenadas em claro dentro do conteúdo decifrado. **22 dos 24 datasets populados preservam todos os registros de origem**, incluindo lugares, rotina manual, contextos, timeline, memórias e histórico digital. Registros atuais adicionais são permitidos nessa comparação por inclusão; ela não comprova ausência de duplicações semânticas nem equivalência integral das configurações.

A comparação estrita de todos os datasets **reprovou**: 76 dos 120 `location_events` brutos antigos foram removidos e o único slot de rotina aprendida não permaneceu. Os eventos removidos vão de 01/09 a 19/09/2026; os 44 retidos vão de 20/09 a 30/09, coerentes com a política existente de 30 dias em `ReconcileWorker`. `RoutineLearningCoordinator` recalcula os últimos 28 dias: o fixture contém um slot WORK_START isolado, mas apenas contextos HOME, sem observações de trabalho que o sustentem. A Jornada original permanece; a rotina manual também. Não alterar a retenção nem preservar aprendizado contraditório apenas para tornar essa comparação verde. Para aprovação completa, ainda é necessário um fixture temporalmente coerente e homologação dos efeitos da manutenção, além da matriz física A/B.

Hashes, contagens e limites: [r8-backup-reexport.json](r8-backup-reexport.json). Arquivos e leitura independente permanecem nos artefatos locais de QA em `app/build/`. Isso acrescenta evidência de R8/SQLCipher/recifragem, sem substituir testes físicos, provedores externos ou interrupções.
