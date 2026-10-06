# Primeiro commit e publicação

O repositório Git foi iniciado na pasta do projeto, na branch `main`. Nenhum commit, remoto ou publicação foi criado automaticamente. Os arquivos do projeto estão preparados para o primeiro commit.

## GitHub Desktop

1. Use **File → Add local repository** e selecione a pasta `Freesic` que contém o README principal e o Gradle Wrapper.
2. Revise as alterações e crie o primeiro commit, por exemplo `Initial commit: Freesic 1.2`.
3. Use **Publish repository**, escolha o nome `Freesic` e a visibilidade desejada.

## Terminal

Execute na raiz do projeto:

```sh
git status
git diff --cached --stat
git commit -m "Initial commit: Freesic 1.2"
```

Depois, crie um repositório vazio no GitHub. Não inicialize esse remoto com outro README ou `.gitignore`. Conecte usando a URL que o GitHub fornecer:

```sh
git remote add origin https://github.com/SEU_USUARIO/Freesic.git
git push -u origin main
```

Substitua `SEU_USUARIO` pelo proprietário escolhido. Se usar autenticação SSH, utilize a URL SSH fornecida pelo GitHub. O projeto não armazena credenciais de autenticação.

## O que entra no commit

Código, recursos, Gradle Wrapper, testes, documentação, imagens de demonstração e o modelo `signing.properties.example`.

O `.gitignore` exclui a chave de assinatura, `signing.properties`, `local.properties`, arquivos `.env`, caches, pastas de compilação, APK/AAB, backups e configurações locais de IDE. Os arquivos privados continuam no computador; não foram apagados. Não use `git add -f` para incluí-los.

## APK para download

O APK de distribuição permanece separado do código. Se quiser disponibilizá-lo no GitHub, anexe-o a uma Release depois da publicação. O aplicativo não busca atualizações nem acessa o GitHub por conta própria.

Mantenha a mesma chave privada para assinar novas versões que atualizem as instalações existentes. Um checkout sem essa chave compila a versão debug com a assinatura padrão do Android; um release sem configuração de assinatura é gerado sem assinatura.

## Se estiver usando apenas o ZIP do código

O ZIP não inclui a pasta interna `.git`. Depois de extrair, execute `git init -b main` e `git add .` na pasta que contém o README antes de seguir os passos de commit acima.
