# Diagnóstico R8 em Linux/KVM

Workflow: `.github/workflows/android-r8-qa.yml` (**R8 development runtime QA**). Pode ser iniciado manualmente em Actions e roda automaticamente na main quando mudam suas ferramentas, as fontes principais do app ou as configurações de build/dependências.

Objetivo: investigar [HVR-NATIVE-API35-001](api35-native-crash.json) fora do host Windows, com AAB de homologação realmente otimizado por R8 e APK derivado. Não usa conta Play, chave de upload nem segredos de distribuição. A variante existente usa o certificado Android Debug do runner; a identificação continua de desenvolvimento, código QA 7. A pipeline protegida de release permanece separada.

O job:

1. Compila `bundleHomologacao`, preservando mapping, commit e árvore de fontes. Verifica a flag de backup, o package de homologação e a versão gerada.
2. Valida AAB/assinatura, deriva o APK com bundletool verificado por SHA-256 e valida certificado, configuração 16 KB, ZIP e ELF de todas as bibliotecas/ABIs.
3. Usa a imagem API 35 `google_apis_ps16k`, revisão 5, emulador build 15917651 e KVM, com quatro cores/4096 MB. Guarda os metadados efetivos do sistema e recursos do host.
4. Exige página 16384 e duas aberturas em processos distintos do APK derivado. Preserva a classificação COLD/WARM real e os logs de boot. ANR de boot reprova o diagnóstico.
5. Em instalação descartável, completa o primeiro acesso com o nome padrão, sem conceder permissões. Lê trinta hierarquias de acessibilidade em Ajustes e trinta em Dados e segurança, exigindo foco no pacote e o mesmo PID. Falha no primeiro erro, sem retry de testes.
6. Preserva hierarquias, foco, Logcat, crashes e motivos de saída mesmo na falha. AAB, APK, mapping e certificado **público** entram nos artefatos; keystore e arquivos de senha ficam fora.

O relatório contém hashes individuais de DEX, bibliotecas nativas e resources.arsc para comparar o código com o APK local que sofreu o crash. Certificados e ambientes de build podem diferir: comparar o conteúdo antes de atribuir uma diferença ao sistema operacional.

O boot usa os scripts compartilhados `install-qa-emulator.sh` e `run-qa-emulator.sh`, também usados pela CI instrumentada e pelo runtime do release. O arquivo oficial fica isolado do SDK, com [origem e hash fixados](linux-emulator-provenance.json). Display/heap são configurados antes de iniciar o Android; o bootstrap aguarda foco e examina ANRs sem enviar a tecla MENU que antecedeu o ANR observado na CI. A execução remota ainda precisa validar essa alteração.

Esse roteiro verifica criação repetida de nós de acessibilidade em telas estáveis. Não executa exportação/restauração nem repete a concorrência exata observada no crash; uma passagem não encerra o defeito nem aprova o roteiro completo de backup. O resultado também não substitui a CI Android completa, assinatura de upload, faixa interna, aparelhos físicos ou campanha prolongada.

Validação local do controlador: instalação limpa do APK R8 em outro AVD API 34/4096, primeiro acesso completo e 70 snapshots com o mesmo PID, incluindo os 30+30 snapshots das telas exigidas. O processador de metadados também foi executado contra os artefatos existentes, conferindo DEX, 12 bibliotecas nativas e resources.arsc; isso não recompilou o app. [Resultado, hashes e limites](r8-qa-local-driver-validation.json). O workflow passou em actionlint e os scripts Bash na verificação de sintaxe; a execução Linux/16 KB ainda depende do run remoto.

Primeira execução remota, [39423287938](https://github.com/rafaelvalquer/Hoodie/actions/runs/39423287938), falhou antes do build: `sdkmanager` não estava no PATH do runner. Nenhum AAB foi gerado e nenhum runtime/teste Android executado. [Diagnóstico](r8-qa-9ed-setup.json). Os comandos usam agora o caminho absoluto do SDK Manager e criam o diretório/log de evidência antes da preparação das ferramentas. A nova execução ainda precisa passar; a falha original permanece registrada.
