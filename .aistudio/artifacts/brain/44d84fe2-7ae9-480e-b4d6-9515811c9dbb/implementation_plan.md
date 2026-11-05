# Tela de Abertura Animada (Splash Screen) com Logo e Verificação de Login

Implementação de uma tela de inicialização animada com a logo oficial e o nome do aplicativo "Assistente de Entregas", realizando a checagem de autenticação em segundo plano antes de redirecionar o usuário para a Home (se autenticado) ou para o Login (se não autenticado).

## User Review & Critical Decisions

> [!IMPORTANT]
> As decisões de design e fluxo foram alinhadas com as preferências selecionadas:
> - **Visual**: Logo com animação suave (escala e fade-in) acompanhada pelo nome "Assistente de Entregas" e indicador de carregamento sutil.
> - **Fluxo pós-checagem**: Se o usuário já possuir sessão ativa no Firebase Auth, navega diretamente para a `Home`. Caso contrário, transiciona suavemente para a tela de `Auth` (Login).

- **Decisão Confirmada 1**: Logo animada com o nome do app exibida como rota inicial padrão (`Screen.Splash`).
- **Decisão Confirmada 2**: Redirecionamento condicional limpo para `Screen.Auth` (quando deslogado) ou `Screen.Home` (quando logado), limpando a Splash da pilha de navegação (`popUpTo`).

---

## 1. Overview & Core Concept

- **O que faz**: Substitui a exibição abrupta da tela de formulário de login no primeiro segundo de inicialização por uma tela de boas-vindas com a identidade visual do app. Enquanto a animação da logo roda e a sessão do usuário é verificada, o app decide suavemente para onde direcionar.
- **Público-alvo**: Entregadores e operadores do app que buscam uma experiência fluida, profissional e sem piscar campos de formulário enquanto o app restaura o estado de login.
- **Valor agregado**: Sensação de polimento nativo e clareza visual, eliminando o flash de interface de login para usuários que já estavam conectados.

---

## 2. User Experience & Visual Design

- **Fluxo do Usuário**:
  1. O usuário abre o app: a tela inicial exibe um fundo moderno com o tema do app e a logo estilizada de entrega centralizada com animação suave de entrada.
  2. Logo abaixo da logo, o título "Assistente de Entregas" surge com tipografia marcante (`FontWeight.Bold`) e um indicador sutil de sincronização.
  3. Após a verificação do estado de autenticação (~1.2s para uma experiência visual agradável e estável), ocorre a transição:
     - **Usuário Conectado**: Transiciona para a `Home` sem passar pelo login.
     - **Usuário Não Conectado**: Transiciona para a `AuthScreen` (Login) de forma natural.
- **Identidade Visual**:
  - *Cores*: Integração total com o design system existente (`BentoBackgroundLight` / `BentoPrimaryDark` / `MaterialTheme.colorScheme`).
  - *Animação*: Transição fluida usando animação de escala e alfa (`animateFloatAsState` ou `Animatable`).
  - *Acessibilidade*: Touch targets e insets de tela cheia com `enableEdgeToEdge()` e `WindowInsets.safeDrawing`.

---

## 3. Key Product Decisions & Trade-Offs

- **Criação de rota dedicada `Screen.Splash`**:
  - *Abordagem*: Adicionar a rota `Splash` no `Screen.kt` e defini-la como `startDestination` padrão no `NavHost`.
  - *Por que*: Evita flashes indesejados, centraliza a lógica de despacho inicial e permite transições previsíveis com `popUpTo(Screen.Splash.route) { inclusive = true }`.
  - *Alternativa descartada*: Colocar a lógica diretamente dentro da `AuthScreen` ocultando campos — causaria recomposições desnecessárias e poluiria o formulário de login.

- **Duração mínima da animação**:
  - *Abordagem*: Garantir um pequeno delay perceptivo (1 a 1.2 segundo) para a logo animar confortavelmente enquanto o Firebase Auth valida a credencial em cache.
  - *Por que*: Se a checagem for instantânea, a tela piscaria rapidamente; se for muito lenta, irritaria o entregador. O timing de ~1.2s garante estética e agilidade.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                      MainActivity                      │
│                           │                            │
│                           ▼                            │
│                     AppNavigation                      │
│                           │                            │
│           (startDestination = Screen.Splash)           │
│                           │                            │
│                           ▼                            │
│                     SplashScreen                       │
│       ┌───────────────────┴───────────────────┐        │
│       ▼                                       ▼        │
│   [Logado?]                               [Deslogado?] │
│       │                                       │        │
│       ▼ (popUpTo Splash)                      ▼        │
│   HomeScreen                             AuthScreen    │
└────────────────────────────────────────────────────────┘
```

- **Mapeamento de Componentes**:
  - `Screen.Splash`: Novo objeto em `com.example.ui.navigation.Screen`.
  - `SplashScreen.kt`: Novo composable em `com.example.ui.screens.splash.SplashScreen` contendo:
    - Card/Box centralizado com a logo do app e nome estilizado.
    - Animação de escala e opacidade via Jetpack Compose.
    - `LaunchedEffect` para aguardar a animação e checar `app.authRepository.currentUser`.
    - Callback `onNavigateToHome` e `onNavigateToAuth`.
  - `AppNavigation.kt`: Adição da rota `composable(Screen.Splash.route)` e alteração do `startDestination` para `Screen.Splash.route`.
