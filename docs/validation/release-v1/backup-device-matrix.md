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

Exportação R8 sem histórico digital, também via SAF/Downloads: o aplicativo concluiu a verificação do arquivo, o manifesto contém `includeDigital=false` e os oito datasets digitais estão ausentes. Os demais registros da exportação anterior permanecem; somente o contexto aberto usa o novo horário de corte, conforme `BackupSchemaAdapter.reconcile`, preservando ID e todos os outros campos. [Contagens e hashes](r8-backup-without-digital.json). Essa comparação entre exports consecutivos não altera a reprovação estrita entre o fixture original e o banco após manutenção documentada acima.

Rejeições pela UI do APK R8 no mesmo runtime: senha pública de teste incorreta produziu “Não foi possível desbloquear o backup. Confira a senha.”; arquivo sintético truncado em 20 bytes produziu “O arquivo está corrompido ou foi modificado.”; formato inválido com cabeçalho completo produziu “Este arquivo não é um backup válido do Hoodie.”. Todos foram rejeitados antes da prévia/confirmação, sem acionar “Restaurar e substituir”. XMLs locais em `app/build/reports/homologacao-runtime/`. A primeira amostra inválida tinha apenas 37 bytes, sendo corretamente classificada como truncada; a expectativa equivocada do roteiro falhou. A amostra foi corrigida para 103 bytes de formato incorreto e somente esse caso foi repetido. Isso valida rejeição antes da ativação, sem alegar uma auditoria integral do banco após cada tentativa.

## Nova exportação após atualização da homologação

O APK derivado do AAB atual (fontes f69f6e6b, código QA 6, R8, certificado de desenvolvimento) abriu o perfil Nuvem já restaurado e exportou pelo SAF/Downloads um novo arquivo de 95.135 bytes. A UI confirmou “Backup criado e verificado com sucesso”. Um leitor independente autenticou 414 unidades AES-GCM, o terminador vazio e a ausência de bytes extras; histórico digital incluído. [Hash, contagens e limites](r8-current-backup-export.json).

Comparado ao export anterior já sujeito à manutenção, todos os registros anteriores permanecem exceto o `endedAt` do contexto 124: antes recortado no horário do snapshot, depois encerrado em horário maior e anterior ao novo snapshot. Todos os outros campos desse contexto são iguais; registros novos de contexto/atividades/timeline/memórias são permitidos. A igualdade estrita de todas as linhas continua falsa. Esta comparação consecutiva não substitui a divergência com o fixture original de 30 dias documentada acima, nem comprova ausência de duplicações semânticas, todas as configurações, provedor externo, interrupção ou dois aparelhos físicos.

## Instalação limpa em outro AVD e identidade da previsão

O APK de homologação R8 f69f6e6b/código QA 6 foi instalado em `Hoodie_API34_Release`, onde o pacote de homologação foi confirmado ausente. UID do destino 10194; origem no AVD API 37.1 usava UID 10229. Apenas o arquivo cifrado foi transferido como dado de usuário, sem banco nem chaves locais. Pelo primeiro acesso → Restaurar meus dados, o SAF abriu a prévia autenticada com 4 lugares, 125 contextos, 4 memórias, rotina e digital incluídos. Após confirmação e reinício, Home/Ajustes exibiram Nuvem, os quatro lugares foram consultados e a rotina conservou dias úteis, 09:00–18:00, almoço 12:00–13:00 e sono 07:00–23:00. As cinco permissões Android de localização, reconhecimento de atividade e notificações permaneceram negadas; o aviso de monitoramento indisponível continuou visível.

A reexportação autenticou 417 unidades (origem 414), com configurações exatamente iguais. A comparação estrita **reprovou**: uma linha de `effective_routine_versions` mudou de ID 4 para 5, sem qualquer outro campo diferente. É a previsão de amanhã, ainda futura no snapshot de origem; todas as versões vigentes/anteriores e os registros de todos os outros datasets preservam os valores de origem. Não aprovar igualdade integral nem ausência de duplicações apenas por essa comparação por inclusão. [Resultado, hashes, escopo e testemunhos XML](r8-independent-restore.json).

O destino usa páginas 4096; essa rodada testa compatibilidade/restauração em Android 14 e chaves independentes, não acrescenta runtime 16 KB. Não houve ANR ou conteúdo no buffer de crash nos logs coletados desse boot com quatro cores. Continua sendo emulador, sem substituir A/B físico, provedor externo, interrupção ou campanha prolongada.

O diagnóstico levou à correção de `prepareTomorrow`, que reinseria até previsões idênticas com ID autogerado. [Revisão de contrato](../../backup/contract-reviews/stable-future-routine-identity.md), [regressões](routine-persistence-regression.json). A correção preserva identidade e não altera formato/schema, mas exige reconstruir R8 e repetir o round-trip antes de aprovar o novo código. A falha registrada acima permanece na evidência do binário anterior.
