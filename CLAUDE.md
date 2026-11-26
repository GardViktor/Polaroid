# Polaroid

## Visão Geral

Polaroid é um aplicativo Android que centraliza promoções de filmes para a câmera **Instax Mini 12** e inclui uma calculadora de custos e lucro para quem vende fotos instantâneas.

## Funcionalidades

- **Calculadora de custo e lucro** (implementada):
  - Entradas: valor pago na compra (filmes + frete), tamanho do pack (botões 10/20/40/50/60/100) × quantidade de packs (contador 1–99, **em experimento**), preço de venda por foto.
  - Custo por foto e **preço mínimo sem prejuízo** (arredondado para cima) aparecem antes do preço de venda; preço abaixo do mínimo gera aviso mas ainda calcula (prejuízo em vermelho).
  - Saídas com preço: lucro por foto, lucro total e margem de lucro (sobre o preço de venda).
  - Calcula enquanto digita; caracteres inválidos são bloqueados na hora; erros só aparecem ao sair do campo; "≈" quando houve arredondamento; prejuízo em vermelho.
- **Buscar filmes** (aba criada, conteúdo "em breve"): ranking de ofertas de filme Instax Mini pelo **preço por foto** ((preço + frete) ÷ fotos), do mais barato ao mais caro. Sem burlar bloqueios de sites: só fontes oficiais (API do Mercado Livre, redes de afiliados). Início direto do app; objetivo futuro é mover a busca para o `backend/` hospedado (cache + chaves protegidas).
- **Álbum** (implementado): câmera dentro do app (CameraX), foto salva na **galeria** em `Pictures/Polaroid` com data, hora e local **aproximado**; grade 3 colunas (Coil para miniaturas), foto aberta com data/hora/local e opção de apagar com confirmação.
  - Data/hora e coordenadas ficam **dentro da foto (EXIF)**; o nome do lugar ("Bairro, Cidade", via Geocoder, precisa de internet) é só cache em SharedPreferences e é redescoberto ao abrir a foto se faltar.
  - Localização começa a ser buscada ao abrir a câmera e nunca trava a foto (limite 5 s → salva sem local). Localização negada não impede a foto.
  - Consulta **fused (Android 12+), rede e GPS ao mesmo tempo** e usa a primeira resposta (`primeiraLocalizacao`). Usar só o provedor de rede falhava em Samsung (One UI 8.5). Sem local, a foto guarda e mostra o motivo (`MotivoSemLocal`: sem permissão / localização desligada / não encontrado a tempo).
  - Interfaces `RepositorioFotos`, `ProvedorLocalizacao`, `ProvedorNomeLocal` isolam o Android para o `AlbumViewModel` ser testado com fakes.
  - Limitação conhecida: após reinstalar o app, fotos antigas continuam na galeria mas não aparecem no Álbum (Android só lista fotos criadas pela instalação atual sem permissão extra de leitura).
- **Relatórios** (em etapas; etapa 1 = registros feita): compras, vendas e perdas de fotos em banco local **Room** (`registros/banco/BancoPolaroid`, tabela única `registros`, esquema versionado em `app/schemas/`).
  - Compra registrada pela calculadora ("Registrar compra", confirmação); venda pela calculadora (preço pré-preenchido) ou "+ Venda"; perda por "− Perda". Tudo editável/apagável na lista.
  - **Nunca datas futuras** (criar ou editar). Datas até o minuto.
  - Dinheiro em **centavos (Long)**; `Centavos.de` rejeita fração de centavo. Venda guarda forma de pagamento e a **taxa congelada** (pontos-base: débito 199, crédito 499, Pix/dinheiro 0); editar mantém a taxa se a forma não mudar.
  - `RegistrosViewModel` é compartilhado (escopo da Activity) entre Calculadora e Relatórios.
  - Próximas etapas: 2) painel (saldo de caixa, lucro com custo médio, estoque com aviso, comparação com período anterior, tela de taxas) 3) gráficos (Vico) 4) exportar CSV.
- **Menu**: barra inferior (`navegacao/Destino` + `AppPrincipal`) com as abas Calcular, Buscar, Álbum e Relatórios; sem biblioteca de navegação (estado salvo com `rememberSaveable`).

## Tech Stack

- **App (Android)**: Kotlin 2.4 + Jetpack Compose (Material 3), Gradle 9.9 + AGP 9.5 (Kotlin embutido no AGP — não usar o plugin `kotlin("android")`), compileSdk 37.1, minSdk 26
- **Testes**: JUnit 5 no módulo `calculadora`, JUnit 4 (`kotlin.test`) no `app`
- **Dispositivo de teste**: celular físico via USB (sem emulador, por espaço em disco)
- **Backend (futuro, para o agregador)**: Python 3.12 + FastAPI + PostgreSQL 17, em Docker — ver `backend/`

## Estrutura

- `calculadora/` — módulo Kotlin puro (sem dependência de Android) com a lógica de custo/lucro (`CalculadoraLucro.calcular`).
  - Dinheiro sempre em `BigDecimal`. **Cada centavo conta**: contas internas com precisão total; arredondar (2 casas, HALF_UP) só o que é inevitável — custo/lucro por foto e margem em divisões não exatas — e sinalizar no resultado (`valoresPorFotoArredondados`, `margemArredondada`) para a tela exibir um aviso.
  - Nunca derivar um valor de outro já arredondado. Lucro total = receita − valor pago (exato).
  - Entradas com fração de centavo (ex.: 10,005) são rejeitadas.
- `app/` — app Android. Tela da calculadora em `app/src/main/kotlin/com/polaroid/app/calculadora/`:
  - `CalculadoraViewModel` (estado + validação, testável sem Android), `EntradaNumerica` (filtro/conversão do que é digitado; aceita vírgula ou ponto como decimal), `Formatador` (R$ 1.234,56 e 33,33%), `CalculadoraTela` (Compose, sem lógica).
  - Regra: lógica de tela fica no ViewModel/objetos puros com teste unitário; Composables só exibem.
- `test.ps1` — roda os testes dos módulos Kotlin puros no Docker, sem SDK local (exclui o `app` via `-PsemAndroid`).

## Comandos

Gradle local precisa do JDK do Android Studio (o Java 25 do PATH não serve para todos os casos):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test              # todos os testes (calculadora + app)
.\gradlew.bat assembleRelease   # APK otimizado (R8) e assinado: app/build/outputs/apk/release/app-release.apk — usar este para testar no celular
.\gradlew.bat assembleDebug     # APK de desenvolvimento (lento, ~12 MB), id com.polaroid.app.debug, nome "Polaroid (dev)"
.\gradlew.bat installDebug      # instala no celular conectado via USB
./test.ps1                      # testes da calculadora no Docker
```

Chave de assinatura do release fica **fora do repositório** em `%USERPROFILE%\.polaroid\` (`polaroid-release.jks` + `keystore.properties`). Nunca commitar. Sem ela o release sai sem assinatura.

SDK em `%LOCALAPPDATA%\Android\Sdk` (gerenciado com `cmdline-tools\latest\bin\android.exe sdk ...`; o `sdkmanager` foi descontinuado).
- `backend/` — API FastAPI + Postgres via Docker Compose (parado por enquanto)
  - Subir: `cd backend && docker compose up -d --build` → `http://localhost:8000/health`
  - Credenciais locais em `backend/.env` (não versionar; modelo em `.env.example`)

## Fluxo de trabalho (obrigatório)

1. Antes de qualquer mudança: explicar o que será feito e pedir autorização.
2. Escrever os testes unitários primeiro.
3. Implementar até todos os testes passarem.
4. Commitar seguindo o padrão abaixo.

## Padrão de commits

Baseado em github.com/GardViktor/Commits: `tipo: descrição`, em português, no imperativo (`adicionar`, `corrigir`), com menos de 50/72 caracteres, commits pequenos e focados.

Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`.
