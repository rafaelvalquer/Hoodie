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
