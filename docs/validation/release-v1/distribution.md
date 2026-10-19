# RELEASE-03 — distribuição assinada

Workflow: `.github/workflows/android-release.yml`, despacho manual a partir de `main`, `release/1.0.0` ou tag `v1.*` pertencente a uma dessas origens. A branch de estabilização deve conter `main`. Exige Android CI concluída com sucesso no mesmo SHA. Não publica na Play.

Criar conta Play, cadastrar o aplicativo e aderir ao Play App Signing. A chave de assinatura usada pela loja e a chave de upload são responsabilidades separadas. Guardar a chave de upload fora do repositório, com cópia segura; a Google gerencia a chave de distribuição conforme a configuração adotada. Não gerar/substituir chaves silenciosamente durante builds.

Configurar Environment `android-signing` com revisores obrigatórios e branches/tags autorizadas. Secrets:

| Secret | Conteúdo |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | Keystore de upload codificado em base64 (não é criptografia) |
| `UPLOAD_STORE_PASSWORD` | Senha do keystore |
| `UPLOAD_KEY_ALIAS` | Alias escolhido |
| `UPLOAD_KEY_PASSWORD` | Senha da chave |

Variables: `UPLOAD_CERT_SHA256` é a impressão pública esperada do certificado de upload, conferida por canal independente. A pipeline falha se não corresponder; base64/arquivo temporário não entra nos artefatos. `PLAY_LAST_VERSION_CODE` registra o maior código já enviado a qualquer faixa (0 se nenhum); manter atualizado após cada envio. O novo código deve superar esse valor e o código de desenvolvimento no repositório. Não colocar segredos em `gradle.properties`, linha de comando ou logs. O diretório temporário tem permissões restritas e o keystore é removido ao terminar.

Bundletool oficial 1.18.2, SHA-256 `378b5434cd1378bef6b2bc527b8c7f0ff2584b273830335bce54d6d0813c8584`, conferido com o digest do asset oficial Google/GitHub. A pipeline verifica o hash antes de executar o JAR.

O despacho informa versionName (`1.0.0-rc1`, estável `1.0.0`) e versionCode novo. Antes do despacho, consultar a Console e registrar que nenhum código maior/igual já foi enviado. O repositório sozinho não comprova a monotonicidade do histórico Play. Não incrementar para um número arbitrário nem declarar publicação realizada.

Saídas: AAB assinado, APK universal derivado via bundletool e verificado por apksigner, SHA, versão, certificado público, SHA-256 do AAB, mapping R8, lint e relatório ELF/ZIP. Testar também splits específicos do aparelho via `bundletool build-apks --connected-device`/`install-apks`, e a faixa interna com APKs assinados pela Play antes de aprovar publicação. O APK local usa a chave de upload e não equivale à assinatura final da Play.

Homologação local: `:app:bundleHomologacao` usa R8, backup integrado, ID e assinatura de desenvolvimento separados. Essa variante protege os dados da instalação pública, mas **não prova atualização da assinatura/ID público**. Testar atualização real usando a mesma faixa e assinatura da Play, preservando os dados existentes.

Testar o APK derivado: instalação limpa, atualização, abertura/reabertura SQLCipher, animações e Home/Diário/Configurações, exportação/restauração, R8/Hilt/Room. Anexar evidência ao SHA e ao hash do AAB. Somente com todos os gates e aprovação explícita configurar credencial Play e envio para faixa interna/fechada. Não há credencial Play nem chave de upload disponível nesta entrega.

Referências: [teste de AAB](https://developer.android.com/guide/app-bundle/test), [bundletool](https://developer.android.com/tools/bundletool), [Play App Signing](https://developer.android.com/studio/publish/app-signing).
