# Validação — Freesic 1.2.0

[← Freesic](../README.md)

Registro da versão publicada, não um indicador de integração contínua. Os dados completos estão em [validacao-1.2.json](validacao-1.2.json), e as saídas das verificações estão em [testes-1.2.txt](testes-1.2.txt).

## Resultados

| Verificação | Resultado registrado |
| --- | --- |
| Lógica JVM | 8 testes aprovados: busca, URIs locais, letras LRC, duração e ganho. |
| Integração | 16 verificações aprovadas de biblioteca, playlists, backup, reprodução, fila, efeitos e segundo plano. |
| Capas e notificação | 9 verificações aprovadas de resolução, substituição e metadados de imagem. |
| Android Lint | 0 erros e 64 avisos; não representa ausência de avisos. |
| Distribuição | APK assinado, assinatura v2 verificada, sem permissão de internet e sem bibliotecas `.so`. |
| Atualização | Instalação do APK final preservou as duas playlists usadas no teste. |

Foram usados emuladores **Android 15 / API 35** e **Android 9 / API 28**, com cobertura diferente entre os fluxos. Também foram conferidos player e volume em uma tela compacta de 320 × 640. Não houve validação em aparelho físico. Na preparação do repositório, uma cópia limpa compilou e passou nos 8 testes JVM sem a chave privada.

## Interface

As capturas mostram a interface executada no Android. O conceito de referência foi recortado nas três telas e normalizado para 411 × 814; as capturas do emulador foram reduzidas proporcionalmente para comparação.

| Revisão | O que foi conferido e ajustado |
| --- | --- |
| [Rodada 1](images/comparacao-1.png) | Estrutura de biblioteca, player e volume; fontes Inter estáticas, cabeçalho, escala da capa e controles. |
| [Rodada 2](images/comparacao-2.png) | Tipografia, alinhamento de menus, espaçamento, controles de reprodução e painel de volume. |
| [Rodada 3](images/comparacao-3.png) | APK assinado: cores, capas, ícones, playlists, seleção múltipla e adaptação a telas menores. |

O [painel de telas](images/interface.png) apresenta a interface final. A biblioteca usa busca acima das abas, mini player integrado e navegação persistente. Player, volume, playlists e diálogos compartilham paleta azul/marinho, fonte Inter e controles consistentes. A seleção de músicas das playlists utiliza a biblioteca do app.

A inspeção da notificação no Android 9 identificou uma tentativa de carregar um URI de álbum sem imagem. A sessão e a notificação passaram a usar o mesmo resolvedor de capas; a verificação da imagem efetivamente publicada foi repetida após a correção.

Não se afirma identidade pixel a pixel com o conceito: conteúdo, progresso e controles refletem dados reais; fontes e ícones variam na renderização; telas pequenas exigem adaptação. Relógio, permissões e cartão de mídia pertencem ao Android, que também define parte das cores e da disposição da notificação.

## Reproduzir os testes de capas

Além da instalação de teste descrita em [Desenvolvimento](DESENVOLVIMENTO.md), o modo de capas exige:

- Um FLAC indexado com título `Horizonte`, imagem incorporada e pasta contendo `FreesicReference`.
- O áudio original de SÓ ROCK 2, sem imagem incorporada, indexado em uma pasta contendo `FreesicReal`.

Esses arquivos não estão no repositório. Sem eles, o teste informa a ausência do arquivo necessário; as nove verificações históricas não são reproduzidas apenas com um clone.

```sh
adb shell am instrument -w -e artwork true com.freesic.app.test/com.freesic.app.FreesicInstrumentation
```

O arquivo original de SÓ ROCK 2 analisado tinha extensão `.mp3`, mas contêiner MP4 e nenhuma imagem incorporada. A correspondência offline usa a capa identificada nos [créditos](../THIRD_PARTY_NOTICES.md). Foram verificados FLAC com imagem, faixa sem imagem, correspondência da capa offline, exclusão de remixes, ausência de arte, bytes nos metadados, notificação publicada, substituição manual e preservação da posição de reprodução.

## Limitações conhecidas

- Formatos, equalizador e amplificação dependem dos decodificadores e efeitos do aparelho; não há FFmpeg empacotado.
- O temporizador não sobrevive ao encerramento forçado do processo. Restrições de bateria do fabricante podem afetar a reprodução em segundo plano.
- A restauração do backup em outro aparelho pode exigir selecionar novamente os arquivos para reconstruir referências locais.
- Letras são carregadas de TXT/LRC locais; não há busca online nem leitura de todas as variantes de tags de letras.
- A versão não inclui edição de tags, recorte ou conversão de áudio, crossfade ou tela de bloqueio própria.
