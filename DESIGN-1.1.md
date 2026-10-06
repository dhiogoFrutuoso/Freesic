# Freesic 1.1 — decisões de interface

Referência: captura do Freesic 1.0 fornecida pelo usuário e conceito `Freesic-conceito-azul.png`, gerado com a ferramenta ImageGen integrada. Implementação Android nativa existente mantida; nenhuma página web ou tela de conceito é embutida no APK. Verificação por emulador e capturas reais substitui a inspeção de navegador, pois o produto é um APK nativo.

Prompt de conceito: player musical Android Freesic, três telas em português (biblioteca, tocando agora, painel de volume), fundo #0B1220, superfícies #162238, destaque #65ADFF, tipografia branca e secundária azul acinzentada, capas em miniaturas, controles separados, ação principal circular, ações Fila/Volume/Letras e ganho de 100% a 300%, sem verde e sem poluição visual.

## Tokens e componentes

- Fundo #0B1220; superfície #162238; destaque #65ADFF; texto #F4F7FC; secundário #9BAEC8.
- Tipografia nativa sans-serif; títulos 23–30sp, faixas 15sp, metadados 12–14sp.
- Margens de 20–24dp e alvos de controle de 48dp. Player rolável para telas menores e fonte ampliada.
- Ícones oficiais Google Material (Apache-2.0), com descrições de acessibilidade nos controles.
- Player: capa em destaque, título até duas linhas, seek, cinco controles e três ações secundárias; timer e velocidade em opções.
- Letras sincronizadas em painel próprio; nenhum cartão de letra vazio ocupa permanentemente a tela.

## Registro de fidelidade

1. Paleta: azul e marinho aplicados a biblioteca, player, painéis, widget e ícone.
2. Hierarquia: capa e título dominam o player; reprodução é a ação principal circular; os controles não formam uma sequência de pílulas.
3. Biblioteca: miniaturas, metadados, busca, coleções, mini player e navegação inferior seguem o conceito. Abas ficam antes da busca para preservar a navegação existente.
4. Volume: painel inferior com sliders independentes e acesso ao equalizador. O botão Pronto é uma adaptação nativa explícita.
5. Conteúdo real: capa vem do arquivo/álbum. O desenho de oceano do conceito não substitui capas reais. Arquivos sem arte mostram ícone musical; capturas usam arquivos gerados só para teste.

## Fluxos

Primeira abertura → permissão de áudio → consulta automática → biblioteca. Novas entradas no MediaStore → observador → atualização sem importação manual. Playlists → abrir coleção → Adicionar músicas → buscar/selecionar → Adicionar. Player → Volume → volume do aparelho / amplificação → Pronto.

O aplicativo permanece sem anúncios, serviços remotos ou permissão INTERNET. O comportamento foi reconstruído com APIs públicas; não se afirma identidade integral com o Lark Player.
