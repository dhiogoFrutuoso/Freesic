# Engenharia e mudanças — Freesic 1.3.0

[← Freesic](../README.md)

Esta versão acrescenta cabeçalho que se oculta na rolagem, seleção de músicas ampliada com ordenação, barra própria para botões de volume, presets de amplificação, cache persistente de capas, nova logo e gestos no player.

## Base de evidências

Foi inspecionado o APK Lark Player **2026.14.7**, SHA-256 `28c50140efcd58c1b95cc53755b38258d8af0240b92549d12756cb0853d7cbcb`. O servidor REA não estava disponível e seu diagnóstico indicou provedores ausentes. Foi usado o fluxo de investigação da skill com leitura complementar direta dos DEX e recursos pelo Androguard. Não são resultados de execução do Lark nem IDs emitidos pelo servidor REA.

As referências abaixo apontam para métodos reais e offsets do pacote. Pseudocódigo torna os nomes legíveis, sem se apresentar como fonte original recuperada integralmente. O código do Freesic continua independente.

## Áudio: o que significa “300%” no Lark

`BasicVolumeAdjustHelper.<clinit>`, em `classes3.dex`, `code_item 0x47e27c`, cria três pares. `g(I,F)`, em `0x47e4e0`, busca o percentual exato e entrega o valor ao controlador. `o.g16.n`, em `0x615338`, passa esse valor ao `LoudnessEnhancer.setTargetGain` quando a rota `use_input_gain` está desligada. [Instruções extraídas](evidencias/audio.json).

| Preset | Alvo do efeito Android |
| --- | ---: |
| 100% | Efeito desligado |
| 150% | 1.000 mB / +10 dB |
| 200% | 3.000 mB / +30 dB |
| 300% | 6.000 mB / +60 dB |

```java
target = preset == 150 ? 1000 : preset == 200 ? 3000 : preset == 300 ? 6000 : 0;
if (target == 0) disableAndReleaseEffect();
else {
    ensureEffectForCurrentAudioSession();
    effect.setTargetGain(target);
    effect.setEnabled(true);
}
```

O Freesic 1.2 usava `2000 * log10(percent / 100)`, resultando em 954 mB para 300%. A versão 1.3 usa a tabela comprovada acima, verifica o controle e o alvo retornado pelo efeito e desativa a amplificação se a aplicação falhar. Na primeira atualização, um ganho antigo é redefinido para 100%, para que o preset muito mais elevado dependa de uma nova escolha.

O rótulo **não corresponde a triplicar a potência acústica**. Trata-se do ganho solicitado ao DSP, que limita/comprime o sinal. A reprodução efetiva depende do aparelho e do conteúdo. O APK também possui outra rota (`use_input_gain`) com valores 4/10/18; sua unidade e implementação final não foram integralmente demonstradas e ela não foi reproduzida. Não se afirma que a rota LoudnessEnhancer seja a ativa em toda instalação do Lark.

### Botões de volume

`BaseActivity.dispatchKeyEvent`, `code_item 0x499b30`, trata keycodes 24/25 e encaminha a ação ao helper. O painel é inserido no conteúdo/decor da Activity. A rotina de ajuste usa volume normal até 100%, depois 150 → 200 → 300; no sentido inverso reduz primeiro o ganho. A chamada ao volume do sistema usa flags zero, sem pedir a barra padrão.

O Freesic reproduz essa sequência e mostra um painel com volume e amplificação. Activity e diálogos encaminham as teclas; fora do app, o Android mantém o controle normal. O volume normal usa os passos reais do aparelho, em vez dos incrementos aproximados de 6,67% encontrados no Lark. Não foi adicionada permissão para sobrepor outros aplicativos.

## Por que as capas podem carregar mais rápido

A evidência vai além da presença da biblioteca Glide no APK:

```text
media.a.c → AudioCover
LarkGlideModule.y → registro AudioCover → InputStream
o.w10.b → o.u10 com largura/altura do alvo
o.u10.d → o.tg1.a → executor → extração da capa
Glide → recursos ativos, memória, trabalhos em andamento e caches
```

O [DEX selecionado](evidencias/imagens.json) mostra três workers, timeout de 30 segundos, cancelamento com `Future.cancel(true)` e limpeza de tarefas canceladas. Os pedidos recebem as dimensões do alvo; o motor reaproveita memória e trabalho em andamento antes de criar outro. O módulo prefere `RGB_565`, multiplicador de tamanho 0,8 e memória ajustada ao aparelho; pedidos específicos podem alterar essas opções.

No Freesic anterior, até uma miniatura podia decodificar 800 px, não havia cache persistente e as linhas eram recriadas ao rolar. Agora:

- As linhas são recicladas e só pedem capas ao desenhar; respostas de uma ligação antiga são rejeitadas.
- Pedidos simultâneos equivalentes compartilham um trabalho. Descartar uma view não cancela outra que ainda depende da mesma capa.
- Miniaturas e player usam resoluções distintas, com decodificação reduzida antes do redimensionamento.
- Memória e disco reaproveitam resultados; ausência de capa também é armazenada temporariamente.
- Para vídeos, são reproduzidas as tentativas de frames no meio, em 5%, 20% e 40% da duração, e o teste de linhas uniformes encontrado em `o.xt`.

Os mecanismos acima se baseiam nas evidências, mas os limites **128/256/512/768 px**, memória **4–24 MiB**, disco **32 MiB / 512 arquivos**, fila de **64 pedidos** e cache negativo de **5 minutos** são escolhas do Freesic. Não são atribuídos ao Lark. A ordem de fontes prioriza a miniatura local do Android; isso também é uma adaptação. Não foi medido um percentual de velocidade em comparação com o Lark.

## Cabeçalho, modal e movimento

O [layout do cabeçalho](evidencias/cabecalho.xml) contém `AppBarLayout` com `scroll|enterAlways`. As abas ficam fora dele no recurso analisado. O Freesic oculta também busca, abas e ações do cabeçalho, conforme o pedido, e mantém o mini player e a navegação. Essa abrangência é uma adaptação; em telas curtas, o conteúdo do cabeçalho pode ser rolado dentro da área disponível.

O seletor de playlist ocupa 88% da altura disponível, com lista flexível, busca e critérios de título, recentes, artista e duração. Repetir o critério inverte o sentido. Essa inversão está comprovada em `SortBottomSheetFragment.l0`; a associação desse menu ao modal específico de adicionar músicas não foi estabelecida no Lark. A seleção permanece ao pesquisar ou ordenar e utiliza os arquivos já indexados.

O [MotionScene do player](evidencias/player-motion-scene.xml) e os [métodos selecionados](evidencias/gestos.json) estabelecem:

| Movimento | Constantes observadas e usadas |
| --- | --- |
| Expansão do player | 600 ms; Bézier `(.56, 1.25, .6, 1)` |
| Troca horizontal | 300 ms; Bézier `(.2, 0, .4, 1)`; capas anterior, atual e próxima |
| Escala na troca | Capa que sai: 0,9 em 30%; capa que entra: 0,9 em 70% |

O Freesic permite arrastar a capa para trocar a faixa, arrastar o player para baixo e o mini player para cima. Animações anteriores são interrompidas antes de um novo gesto; limites da fila retornam a capa à posição inicial. As preferências do Android para desativar animações são respeitadas.

**Limite de fidelidade:** o Lark altera curvas e duração conforme estados adicionais em `MotionAudioPlayerFragment.y1`. O Freesic usa views nativas próprias, não a máquina inteira de estados do MotionLayout. O arrasto do mini player, limiares de velocidade, cancelamento, fechamento em 400 ms e feedback de botão em escala 0,92 são adaptações explícitas. Portanto, esta versão não é apresentada como uma cópia integral da física de todas as telas.

## Logo

A imagem fornecida substitui a identidade anterior no cabeçalho, launcher, widget e placeholders sem faixa. Os cantos externos foram tornados transparentes: quatro cantos com alfa zero e nenhum pixel branco opaco na imagem final. A notificação pequena usa uma versão vetorial monocromática de ondas, porque esse ícone é tratado como máscara pelo Android; as capas das músicas continuam sendo as imagens das respectivas faixas.

## Validação e cobertura

Os resultados desta versão ficam em [validacao-1.3.json](validacao-1.3.json). Os testes instrumentados incluem cancelamento de gesto, limite da fila, cache de memória/disco, troca da mesma URI, pedidos compartilhados e confirmação de 6000 mB na sessão ativa. A validação em emulador não mede pressão sonora nem substitui testes em aparelho físico.

## Correção visual 1.3.1

A onda da logo foi simplificada com traços mais espessos para reduzir a confusão entre linhas em tamanhos pequenos. O recurso compartilhado usa filtragem e mipmaps; a margem do ícone adaptativo passou de 18 dp fixos para 16,666667% da camada. A arte anterior permanece apenas como fixture do teste, fora do APK de distribuição. [Comparação renderizada no Android](images/logo-1.3.1.png) e [validação da correção](validacao-1.3.1.json).

## Ganho adicional no Freesic 1.3.2

A escala visual continua em 100%, 150%, 200% e 300%. O preset máximo agora solicita **6.600 mB (+66 dB)**; os demais continuam em 0, 1.000 e 3.000 mB. Esse aumento de 6 dB é uma escolha do Freesic solicitada pelo usuário, não um valor encontrado no Lark. A tabela e as evidências anteriores documentam a versão 1.3.0.

Na primeira execução após atualizar, a amplificação volta a 100% para evitar aplicar o novo ganho automaticamente a um valor antigo salvo. Falha, indisponibilidade ou rejeição do alvo pelo Android continua desativando o efeito. A API aplica compressão a sinais que ultrapassam sua faixa; aceitar 6.600 mB não comprova aumento acústico de 6 dB, e pode haver mais distorção. [Referência oficial do LoudnessEnhancer](https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer).


## Modo extremo — Freesic 1.3.4

A pedido do usuário, 300% passa a solicitar **10.000 mB (+100 dB)**. 150% e 200% continuam solicitando 1.000 e 3.000 mB. Esse máximo é uma extensão própria, diferente do Lark. Não representa 100 dB SPL nem uma garantia de aumento acústico. O controle permanece limitado a 300%; não existe ganho numérico ilimitado.

O ganho extremo exige confirmação em uma modal com aviso de distorção e risco ao equipamento e à audição. Cancelar mantém o ganho anterior. Enquanto o aviso estiver aberto, aumentar volume não o contorna; diminuir continua disponível. A camada de reprodução também recusa ganho extremo sem confirmação. O novo perfil começa em 100%, e as verificações de aceitação e controle do efeito Android são preservadas. Não são desativados os limitadores ou proteções da plataforma.
