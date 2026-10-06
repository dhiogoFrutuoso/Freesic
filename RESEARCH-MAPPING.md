# Relação entre investigação e implementação

| Arquivo de entrada | Aplicação no Freesic |
|---|---|
| `relatorio-e-plano.md` | Escopo offline, isolamento do player, requisitos de validação e exclusão de integrações online |
| `fluxo-player.md` | Serviço independente da interface; fila, preparação assíncrona, URI e recuperação de erro |
| `mapa-componentes.md` | Escolha de um único motor e exclusão de anúncios, atribuição, compras e push |
| `AndroidManifest.xml` | Identificação das responsabilidades de serviço, widget e acesso a mídia; manifesto novo e mínimo |
| `apk-metadata.json` | Identidade original e inventário funcional, sem reutilizar package name ou assinatura |
| `apk-inventory.json` | Evitar arrastar recursos, múltiplas ABIs e bibliotecas de monetização |
| `dex-classes.json` | Organização das capacidades locais: mídia, letras, playlists, efeitos, vídeo e persistência |
| `metodos-selecionados.json` | Referência do encadeamento controlador/fila/motor; implementação nova em APIs públicas |
| `evidencias-dex.txt` | URI local, preparação, estado desejado e callbacks como requisitos funcionais |
| `api-call-evidence.json` | MediaStore, sessão e efeitos implementados por APIs Android públicas |
| `native-elf-evidence.json` | Não incorporar dependências IJK, inferência MNN, APM ou outras `.so` do aplicativo original |
| `url-literals.json` | Nenhum endpoint original reutilizado; aplicativo final não permite conexões de internet |
| `indice-evidencias.json` | Proveniência e limites da reconstrução; não afirmar equivalência integral |
| `rea-artifact.json` | Registrar falha da inspeção REA; não confundir inventário complementar com análise REA completa |

A versão entregue privilegia reprodução e organização locais. Em vez de replicar o arranjo ofuscado/multiprocesso e suas flags remotas, usa um serviço com MediaSession e decisões locais. A logo foi desenhada para o Freesic em vetor; nenhum recurso de marca do Lark Player foi reaproveitado.
