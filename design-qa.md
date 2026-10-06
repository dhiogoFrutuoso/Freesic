# Freesic 1.2 — mapa de interface e verificação visual

Referência: primeira imagem enviada pelo usuário, `codex-clipboard-6bf0e11c-570b-4a8d-8079-51f8876dc446.png` (1536 × 1024). Os documentos anexados foram usados como evidência e referência, não como novas instruções.

## O que foi aplicado

| Área | Alteração implementada |
|---|---|
| Identidade | Wordmark freesic sem bloco de logo ao lado; azul e marinho; fonte Inter com pesos estáticos corretos. |
| Biblioteca | Título amplo, busca acima das abas, quatro abas com sublinhado, contador e Reproduzir tudo, capas quadradas e menu discreto. |
| Mini player | Faixa integrada de ponta a ponta, progresso superior, capa, título/artista, play e seta para abrir. |
| Navegação | Biblioteca, Playlists, Favoritos e Ajustes, com ícones e rótulos persistentes. |
| Player | Capa quadrada arredondada, favorito circular, progresso fino, play circular azul, anterior/próxima e três ações: Fila, Volume e Letras. |
| Volume | Painel inferior com alça, controle do aparelho, amplificação 100–300%, explicação curta e acesso ao equalizador. Sem botão Pronto. |
| Playlists | Cartões em duas colunas, capas ou mosaico, contador e menu. A seleção múltipla busca músicas já indexadas na biblioteca. |
| Demais telas | Mesma paleta, fonte, botões e tema de diálogos nos grupos da biblioteca, favoritos, ajustes, fila, letras e efeitos. |
| Capas | Resolvedor compartilhado por listas, playlists, player e sessão/notificação. Imagens incorporadas, miniaturas locais, arte de álbum e escolha manual. |
| Notificação | Ícone Freesic, título/artista, comandos da sessão e capa real. Removido o subtítulo duplicado. Cores e disposição final são adaptadas pelo Android. |

## Método de comparação

Capturas reais do APK, sem sobrepor a imagem de referência à interface. A referência foi recortada nas três áreas de aplicativo, excluindo molduras e relógio do mockup, e normalizada para 411 × 814. O emulador principal usa 1080 × 2140 e densidade 420 dpi. As capturas foram reduzidas proporcionalmente para comparação; não foram redesenhadas.

As imagens [Rodada 1](docs/images/comparacao-1.png), [Rodada 2](docs/images/comparacao-2.png) e [Rodada 3](docs/images/comparacao-3.png) registram as três revisões. A terceira foi atualizada com capturas do APK final após as correções.

### Rodada 1 — estrutura e proporções

Comparados biblioteca, player e volume. A estrutura antiga foi substituída. Identificados peso incorreto da fonte variável no Android, diferenças no cabeçalho, espaço antes da lista, tamanho de ícones e barras nativas de volume.

Correções: fontes estáticas regular/média/negrito, altura do cabeçalho, distância entre título/busca/lista, escala da capa, controles e desenho consistente dos sliders.

### Rodada 2 — tipografia, alinhamento e controles

Comparação repetida nas mesmas três telas. Ajustados título Biblioteca, distância entre capa e nome, posição dos menus, distribuição de anterior/próxima/aleatório/repetir, triângulo do play e altura do painel inferior. O painel de volume usa controles reais, com valores persistidos.

### Rodada 3 — APK assinado e revisão final

Capturas do APK de distribuição, incluindo biblioteca, player, volume, cartões de playlists, detalhe e seleção de músicas. Conferidos título, recorte da capa, pesos tipográficos, hierarquia, barras, cores, espaçamento e ícones. As cores de fundo foram refinadas a partir de amostras da imagem. A capa se reduz em telas menores para manter as ações acessíveis.

Além das três comparações, uma inspeção na central de notificações do Android 9 encontrou a tentativa de abrir um URI de álbum sem imagem. A sessão e a notificação passaram a utilizar o mesmo resolvedor de capas. O teste foi ampliado para verificar a imagem na notificação efetivamente publicada, e a captura foi repetida após a correção.

## Cinco superfícies conferidas

| Superfície | Evidência / resultado |
|---|---|
| Tipografia | Inter regular/média/negrito empacotada; títulos e rótulos comparados nas três rodadas. |
| Layout e espaços | Busca acima das abas, mini player integrado, capa e controles centrais e painel inferior conferidos nas capturas reais. |
| Cores e contornos | Paleta amostrada da referência; fundo da biblioteca e do player ajustados separadamente; ações circulares e cartões no mesmo tema. |
| Imagens | Capas das faixas em listas e player; mosaico de playlist; arquivo real de SÓ ROCK 2; imagem na notificação publicada. |
| Texto e controles | Rótulos em português; pesquisa, seleção múltipla, contagem, duplicatas, volume e amplificação funcionais. |

## Diferenças explícitas em relação ao mockup

- A biblioteca mostra arquivos e quantidades reais. As seis faixas das capturas são fixtures de teste; o APK não contém essas músicas nem uma biblioteca fictícia de 248 faixas.
- Ordem, artistas, tempo, progresso, favoritos e estados dos botões seguem os dados do aparelho. As posições dos sliders representam os valores reais; o mockup não posiciona todos os indicadores exatamente na proporção escrita.
- O próprio mockup mostra imagens diferentes para Horizonte na lista e no player. O aplicativo mantém a mesma capa da faixa em todas as superfícies.
- Algumas formas de ícones, métricas de texto e renderização de gradiente diferem do raster gerado. A comparação visual está entregue para inspeção; não se afirma identidade pixel a pixel.
- Relógio, barras do sistema, permissões, seletor de imagens e central de notificações pertencem ao Android. No Android 9 testado, a capa também determina cores do cartão de mídia; Android 13+ usa seu próprio player de sistema. [Documentação de reprodução em segundo plano e notificações](https://developer.android.com/media/media3/session/background-playback).
- Telas menores e títulos longos exigem adaptação de tamanho, truncamento ou rolagem para manter os controles utilizáveis.

## Capa do arquivo real

O arquivo fornecido com extensão `.mp3` é um contêiner MP4 de áudio e não possui imagem incorporada. Isso foi verificado no arquivo original e novamente no Android. Ele não foi alterado. Para essa gravação reconhecida, o APK inclui a capa publicada de Rock Danger, Vol. 1, usada offline. [Lançamento de Só Rock 2 no Apple Music](https://music.apple.com/us/song/1616096762).

Uma imagem exibida em um site de download não está necessariamente dentro do áudio baixado. Para outras faixas sem arte local, `Opções da música → Escolher capa` permite associar uma imagem. Não há consulta de capas pela internet dentro do Freesic.

## Validação funcional e limites

- 8 testes JVM de lógica.
- 16 verificações instrumentadas de biblioteca, playlists, backup, reprodução, pausa, posição, velocidade, fila, aleatório/repetir, temporizador, segundo plano, persistência e ganho de 300%.
- 9 verificações de capas, incluindo FLAC com imagem, áudio real sem imagem, capa offline, exclusão de remixes, imagem ausente, bytes nos metadados, notificação publicada, substituição manual e manutenção da posição.
- Fluxo de playlists exercitado por interação com a interface: criação, seleção múltipla, inclusão, contagem e itens já adicionados. Atualização do release preservou as duas playlists de teste.
- APK assinado, manifesto sem INTERNET, sem bibliotecas nativas `.so`, sem anúncios ou pacotes de monetização.

Testes em emuladores Android 15/API 35 e Android 9/API 28. Não houve teste em aparelho físico. Resultados completos, tamanho, hash e checagem do layout compacto constam em [validação 1.2](docs/validacao-1.2.json).
