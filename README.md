# 📦 Assistente de Entregas

> **Assistente flutuante nativo para otimização de rotas e preenchimento automático em aplicativos de entrega.**

O **Assistente de Entregas** é uma solução Android avançada desenvolvida com **Kotlin** e **Jetpack Compose**. O aplicativo combina um **Balão Flutuante (Overlay)** interativo com um **Serviço de Acessibilidade** especializado para detectar endereços, identificar recebedores cadastrados e automatizar o preenchimento de formulários (nome, documento e assinatura digital) diretamente nos aplicativos de entrega parceiros.

---

## 🚀 Funcionalidades Principais

### 🎈 Balão Flutuante Inteligente (Overlay)
- **Acesso Rápido em Qualquer Tela**: Permanece visível sobre outros aplicativos para acionamento imediato durante as rotas.
- **Detecção Automática de Endereço**: Identifica a localização/endereço exibido no app de entregas e sugere instantaneamente a pessoa cadastrada correspondente.
- **Seleção Rápida de Recebedores**: Permite alternar entre múltiplos recebedores cadastrados no mesmo endereço com um único toque.
- **Indicador Visual de Status**:
  - **Gradiente Dark / Azul**: Indica status ativo ou de automação em andamento.
  - **Badge Amarela Piscante**: Sinaliza quando o *Modo Aprendizado/Mapeamento* está ativo.

### ⚡ Automação por Acessibilidade
- **Preenchimento Automático de Campos**: Insere o nome e o documento do recebedor nos campos corretos do aplicativo de entregas.
- **Injeção de Gestos de Assinatura**: Reproduz vetorialmente a assinatura cadastrada na área de assinatura da tela.
- **Centralização Dinâmica**: Calcula a posição exata da caixa de assinatura para garantir que os gestos fiquem perfeitamente centralizados no canvas de destino.
- **Controle de Velocidade**: Ajuste fino do ritmo de reprodução dos gestos (*Ultra Lento, Lento, Médio, Rápido*) para máxima compatibilidade entre dispositivos.

### 💾 Gestão Local de Dados (Room Database)
- **Cadastro de Pessoas & Endereços**: Registre recebedores, documentos e associações de endereços com suporte a busca rápida.
- **Coleta e Armazenamento de Assinaturas**: Canvas interativo para desenhar e salvar traços de assinatura em formato vetorial.
- **Funcionamento 100% Offline**: Todos os dados permanecem salvos localmente com total privacidade e segurança.

### 🧪 Laboratório de Automação & Testes
- **Ambiente de Homologação Integrado**: Simula o preenchimento de dados e a reprodução de assinaturas em um formulário de testes antes da aplicação em produção.
- **Área de Assinatura Interativa**: Permite desenhar, visualizar em tempo real e testar a automação no próprio laboratório.

### 📊 Diagnóstico & Logs
- **Histórico de Logs em Tempo Real**: Acompanhe o ciclo de vida dos eventos de acessibilidade e da automação.
- **Relatórios & Exportação**: Envio fácil de logs de diagnóstico para verificação de incompatibilidades ou ajustes de mapeamento.

---

## 🛠️ Arquitetura e Tecnologias

- **Linguagem**: Kotlin
- **Interface Gráfica**: Jetpack Compose (Material Design 3)
- **Automação de Sistema**: Android `AccessibilityService` + `GestureDescription` API
- **Superposição de Tela**: `WindowManager` (System Alert Window / Overlay)
- **Persistência de Dados**: Room Database (SQLite) + Kotlin Symbol Processing (KSP)
- **Concorrência**: Kotlin Coroutines & `StateFlow`
- **Injeção de Dependências**: Constructor Injection + Repositories
- **Navegação**: Jetpack Navigation Compose com rotas tipadas

---

## 🔒 Permissões Necessárias

Para operar com suporte a automação sobre outros aplicativos, o aplicativo solicita:

1. **Sobreposição a Outros Apps (`SYSTEM_ALERT_WINDOW`)**:
   - Permite que o balão flutuante permaneça acessível durante o uso de outros aplicativos.
2. **Serviço de Acessibilidade (`BIND_ACCESSIBILITY_SERVICE`)**:
   - Necessário para ler o conteúdo da tela (somente endereços e campos de entrega) e efetuar as ações de preenchimento e desenho de assinatura.

> ℹ️ **Privacidade**: O aplicativo não envia dados para servidores externos. Todo o processamento de telas e banco de dados é realizado estritamente no próprio dispositivo.

---

## 📱 Estrutura do Projeto

```text
app/src/main/java/com/example/
├── accessibility/           # Motor de Automação e Serviço de Acessibilidade
│   └── AccessibilityAutomationEngine.kt
├── data/                    # Entidades, DAOs e Banco de Dados Room
│   ├── AppDatabase.kt
│   ├── dao/                 # Interfaces de Acesso a Dados (PersonDao, AddressDao)
│   └── model/               # Modelos de Dados (Person, Address, SignatureData)
├── repository/              # Camada de Repositório e Gestão de Estado
├── service/                 # Serviço de Balão Flutuante (FloatingBubbleService)
├── ui/                      # Camada de Apresentação Jetpack Compose
│   ├── components/          # Componentes Reutilizáveis (SignatureCanvas, Badges)
│   ├── navigation/          # Rotas e Grafo de Navegação
│   ├── screens/             # Telas da Aplicação (Home, Cadastro, Lab, Diagnóstico)
│   └── theme/               # Temas, Cores e Tipografia Material 3
└── util/                    # Utilitários, Assistente de Voz e Logger de Crash
```

---

## 🏁 Como Executar o Projeto

### Pré-requisitos
- **Android Studio**: Jellyfish (2023.3.1) ou superior
- **JDK**: Java 17
- **Versão do Android**: Android 8.0 (API nível 26) ou superior

### Passo a Passo
1. Clone o repositório ou abra a pasta do projeto no Android Studio.
2. Aguarde a sincronização do **Gradle** com o **Version Catalog** (`gradle/libs.versions.toml`).
3. Execute a compilação no dispositivo ou emulador através da task `:app:assembleDebug` ou acionando **Run** no Android Studio.
4. Ao abrir o aplicativo pela primeira vez:
   - Conceda a permissão de **Sobreposição de Tela**.
   - Ative o **Assistente de Entregas - Automação** no menu de **Acessibilidade do Android**.

---

## 📄 Licença

Desenvolvido para otimização de entregas e produtividade operacional.
