# Homologação física — início da execução

Disponibilidade confirmada em 19/10/2026. A campanha ainda não começou. Preencher os modelos, versões Android, fabricante e capacidade de página de cada aparelho antes de associar resultados à matriz.

Atualização do responsável na mesma data: nenhum aparelho disponível para conectar neste momento. A execução abaixo permanece pendente; não há resultado físico aprovado.

## Pacote de teste

Usar `homologacao`, com R8, backup habilitado e ID separado `com.hoodie.app.homologacao`. O nome da Activity continua `com.hoodie.app.MainActivity`. A assinatura de desenvolvimento permite os testes locais; não representa assinatura de upload ou aprovação da Play. Esta instalação fica separada do Hoodie de uso pessoal.

O APK de teste deve vir do bundle: compilar `:app:bundleHomologacao`, validar o AAB com bundletool e gerar o conjunto de APKs usando a chave de desenvolvimento correspondente à variante. O candidato local já gerado encontra-se em `app/build/outputs/bundle/homologacao/hoodie-homologacao-from-bundle.apk`; conferir a localização e o hash contra `homologacao-native-static.json` antes de instalar. Anotar SHA do código e mudanças locais se houver; um candidato de worktree não recebe aprovação de RC.

## Conexão e instalação

1. No aparelho destinado a testes, habilitar as opções do desenvolvedor e a depuração USB, conectar ao computador e aceitar a autorização exibida no telefone.
2. Executar `adb devices -l`. Registrar o serial do aparelho físico; não escolher um emulador por engano. Se aparecer `unauthorized`, resolver a autorização no telefone antes de continuar.
3. Executar `adb -s SERIAL shell getprop ro.product.model`, `adb -s SERIAL shell getprop ro.build.version.release` e `adb -s SERIAL shell getconf PAGE_SIZE`. Registrar os resultados na evidência do aparelho.
4. Instalar o APK derivado com `adb -s SERIAL install -r CAMINHO_DO_APK`. Para validar atualização, conservar a instalação de teste anterior e usar a mesma chave. Não desinstalar nem executar `pm clear` no Hoodie pessoal.
5. Abrir `adb -s SERIAL shell am start -n com.hoodie.app.homologacao/com.hoodie.app.MainActivity`. Confirmar a versão em Ajustes e a presença de Dados e segurança.

Todos os comandos que modificam aparelho precisam indicar o serial. Não executar um roteiro destrutivo sobre um aparelho apenas porque ele aparece conectado. A substituição na restauração deve acontecer em instalação de teste com dados sintéticos ou com cópia recuperável conferida pelo responsável.

## Primeira sessão assistida

No aparelho A, cadastrar lugares fictícios Casa e Trabalho, rotina e uma atividade manual. Conferir Diário, lugares, configurações e permissões. Exportar com e sem digital pelo seletor real de documentos. Cancelar uma exportação e confirmar que os dados continuam acessíveis. Medir separadamente o tempo de derivação/validação da senha no hardware físico.

No aparelho B, instalar a mesma versão sem dados anteriores. Copiar o arquivo pelo mecanismo escolhido pelo responsável, restaurar pelo primeiro acesso e conferir lugares, horários, IDs/relações históricas, memórias e rotina. As permissões de Android não são transferidas pelo arquivo: autorizar novamente, reconstruir geofences e registrar nova atividade. Confirmar que a atividade se agrega ao histórico restaurado sem duplicações. Reiniciar o aplicativo e o aparelho; repetir a conferência.

Os arquivos sintéticos ricos de 30 dias podem complementar o teste: ver `backup-device-matrix.md`. Não usar sua senha pública em dados pessoais. Guardar arquivos, capturas e logs de rotina real fora do Git e revisar dados pessoais antes de compartilhar evidência.

Executar a matriz funcional e de interrupções em `backup-device-matrix.md`, registrando cada resultado e falha. Só então iniciar os sete dias reais descritos em `battery-geofence-results.md`. Não substituir essa duração por simulação de relógio ou emulador.

## Registro mínimo de cada resultado

Data/hora, responsável, modelo/Android/serial pseudonimizado, versão e código, SHA do código, hash do AAB/APK, certificado, página de memória, cenário, pré-condição, resultado esperado/observado e caminho da evidência. Informar se a evidência é instalação limpa, atualização ou restauração sobre dados de teste. Falha bloqueante deve gerar item de correção; não repetir até passar e omitir a primeira falha.

Conta Play e chave de upload ainda pendentes. Os testes locais podem avançar; instalação pela faixa interna, beta e publicação exigem esses recursos e os respectivos gates.
