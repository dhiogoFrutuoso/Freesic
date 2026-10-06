# Desenvolvimento

[← Freesic](../README.md)

## Ambiente

| Componente | Versão |
| --- | --- |
| JDK | 17 ou 21; código Java 17 |
| Android SDK | Plataforma 36; mínimo do aplicativo: API 26 |
| Android Build Tools | 35.0.0 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.13 |
| AndroidX Media3 | 1.9.3 |

Clone o repositório e abra a pasta no Android Studio:

```sh
git clone https://github.com/dhiogoFrutuoso/Freesic.git
cd Freesic
```

Configure `ANDROID_HOME` ou crie `local.properties` com `sdk.dir` apontando para seu SDK. O Android Studio também pode configurar esse arquivo. A primeira compilação baixa o Gradle e as dependências; essa conexão é feita pelo computador de desenvolvimento.

## Compilar e testar

No Windows:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

No Linux/macOS:

```sh
./gradlew assembleDebug testDebugUnitTest
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`. Sem configuração de assinatura pessoal, o build usa a chave debug padrão do Android. Esse APK pode ter assinatura diferente da release publicada.

### Testes no Android

Use um **emulador ou perfil de teste**: a instrumentação limpa as preferências do Freesic e cria arquivos e playlists de demonstração. O fluxo principal gera seus próprios WAVs e usa APIs de armazenamento do Android 10 ou superior. Instale o app, abra-o e conceda acesso a áudio antes de executar:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.freesic.app.test/com.freesic.app.FreesicInstrumentation
```

Em Linux/macOS, substitua `.\gradlew.bat` por `./gradlew`. Os testes extras de capas exigem arquivos externos; os pré-requisitos e resultados estão em [Validação](VALIDACAO.md).

## Gerar uma release

Copie [signing.properties.example](../signing.properties.example) para `signing.properties` e preencha o caminho, alias e senhas da sua chave. Para atualizar instalações existentes, use a mesma chave da versão instalada.

```powershell
.\gradlew.bat assembleRelease lintRelease
```

Com a assinatura configurada, o APK fica em `app/build/outputs/apk/release/app-release.apk`. Sem ela, a saída é `app-release-unsigned.apk`, que precisa ser assinada antes da instalação. A configuração pessoal, quando presente, também assina os builds debug.

O [.gitignore](../.gitignore) exclui chaves, senhas, configuração local do SDK e artefatos de build. Mantenha um backup privado da chave. Ao publicar uma nova versão, atualize `versionCode` e `versionName` em `app/build.gradle`, valide o APK e anexe-o à release correspondente com seu SHA-256.

## Organização do código

As classes ficam em [`app/src/main/java/com/freesic/app`](../app/src/main/java/com/freesic/app).

| Componente | Responsabilidade |
| --- | --- |
| `MainActivity` | Biblioteca, coleções, player e ajustes. |
| `PlaybackService` | ExoPlayer, MediaSession, foco de áudio, efeitos e temporizador. |
| `Library` / `Track` | Consulta ao MediaStore, metadados e identidade por URI. |
| `Store` | Playlists, favoritos, fila, preferências e backup. |
| `Artwork` | Resolução e cache de capas compartilhados entre interface e sessão. |
| `MusicLogic` | Busca, duração, parser LRC e conversão de ganho. |
| `BrandView` / `Icons` / `UiTheme` | Identidade visual, ícones e fundos. |
| `FreesicWidget` | Controles na tela inicial do Android. |

A interface usa views nativas. O serviço mantém a reprodução independente da Activity e aceita origens locais. O manifesto remove as permissões de internet; não são empacotados motores nativos de áudio ou SDKs de monetização.

## Origem e decisões

O Freesic foi implementado com APIs públicas Android, usando uma investigação do Lark Player como referência de comportamento. Não incorpora DEX, bibliotecas `.so`, layouts ou recursos de marca do APK original, nem afirma equivalência completa com ele.

O [registro de entradas](../research-inputs.json) preserva a identificação e os hashes dos 14 artefatos de pesquisa. Eles orientaram quatro decisões: serviço de reprodução separado da interface; biblioteca baseada em URIs locais; playlists, letras e efeitos persistidos no aparelho; exclusão de endpoints, anúncios, compras, atribuição e push. Os arquivos de pesquisa não são dependências de compilação. O registro REA documenta uma falha de inspeção; o inventário complementar não constitui uma análise REA completa.
