# Changelog - Novas Funcionalidades

## 🚀 Versão Atual (21/03/2026)

### ✨ 1. **Configurações Avançadas do Aria2**

Expandimos significativamente as opções de configuração do Aria2 para dar mais controle sobre os downloads:

#### **Novas Configurações Adicionadas:**

**Grupo: Downloads**
- ✅ **Tamanho Mínimo de Split** (`min-split-size`)
  - Padrão: 20M
  - Exemplo: 20M, 1G
  - Define o tamanho mínimo para dividir arquivos em múltiplas partes

- ✅ **Limite Máximo de Download** (`max-download-limit`)
  - Padrão: 0 (ilimitado)
  - Exemplo: 1M, 10K
  - Limita a velocidade de download

**Grupo: Tentativas e Timeouts**
- ✅ **Máximo de Tentativas** (`max-tries`)
  - Padrão: 10
  - Número de tentativas antes de falhar

- ✅ **Tempo de Espera entre Tentativas** (`retry-wait`)
  - Padrão: 5 segundos
  - Intervalo entre tentativas de download

- ✅ **Timeout de Conexão** (`timeout`)
  - Padrão: 60 segundos
  - Tempo máximo para estabelecer conexão

**Grupo: Avançado**
- ✅ **Método de Alocação de Arquivo** (`file-allocation`)
  - Padrão: none
  - Opções: none, prealloc, falloc
  - Define como o espaço em disco é alocado

- ✅ **Intervalo de Auto-Save** (`auto-save-interval`)
  - Padrão: 60 segundos
  - Frequência de salvamento do progresso

- ✅ **Continuar Downloads Incompletos** (`continue`)
  - Padrão: true (ativado)
  - Permite retomar downloads interrompidos

#### **Localização:**
`Configurações > Aria2`

#### **Arquivos Modificados:**
- `core/main/src/main/java/com/rk/settings/Settings.kt` - Adicionados 8 novos settings
- `core/main/src/main/java/com/rk/terminal/ui/screens/settings/Aria2Settings.kt` - UI expandida

---

### 👥 2. **Sistema de Solicitações de Amizade no Perfil**

Agora você pode ver e gerenciar pedidos de amizade diretamente na tela de perfil!

#### **Funcionalidades:**

✅ **Visualização de Pedidos Pendentes**
- Lista automática de solicitações recebidas
- Mostra foto de perfil e nome do solicitante
- Contador de pedidos pendentes

✅ **Ações Rápidas**
- **Botão Aceitar** (ícone ✓ verde)
  - Adiciona o usuário como amigo imediatamente
  - Atualiza a lista automaticamente
  
- **Botão Recusar** (ícone × vermelho)
  - Remove o pedido
  - Não adiciona à lista de bloqueados

✅ **Interface Aprimorada**
- Cards elevados com design Material 3
- Responsivo e otimizado para Android
- Atualização automática após ações

#### **Como Funciona:**

1. Abra seu perfil (ícone de usuário)
2. Se houver pedidos pendentes, aparecerá a seção "Solicitações de Amizade (X)"
3. Clique em ✓ para aceitar ou × para recusar
4. A lista atualiza automaticamente

#### **Endpoints da API Utilizados:**
- `GET /profile/friend-requests` - Busca pedidos pendentes
- `PATCH /profile/friend-requests/{id}/accept` - Aceita pedido
- `PATCH /profile/friend-requests/{id}/refuse` - Recusa pedido

#### **Arquivos Modificados:**
- `core/main/src/main/java/com/rk/terminal/ui/screens/home/HydraModels.kt` - Novos modelos
- `core/main/src/main/java/com/rk/terminal/ui/screens/home/ProfileScreen.kt` - UI e lógica

---

## 📋 **Comparação Antes/Depois**

### **Configurações Aria2**

| Antes | Depois |
|-------|--------|
| 4 configurações básicas | 12 configurações completas |
| Sem controle de tentativas | Controle total de retry logic |
| Sem limite de velocidade | Limite configurável |
| Interface simples | Interface organizada em grupos |

### **Sistema de Amizade**

| Antes | Depois |
|-------|--------|
| ❌ Pedidos invisíveis | ✅ Lista visível no perfil |
| ❌ Sem ações rápidas | ✅ Aceitar/Recusar com 1 toque |
| ❌ Precisava ir no site | ✅ Tudo no app |
| ❌ Sem contador | ✅ Contador de pendências |

---

## 🎯 **Próximas Melhorias Sugeridas**

Baseado nas funcionalidades do HydraPc (veja `SUGESTOES_ANDROID.md`):

1. **Rastreamento de Tempo de Jogo** (Alta Prioridade)
2. **Status "Jogando Agora"** (Alta Prioridade)
3. **Sistema de Conquistas** (Média Prioridade)
4. **Notificações Push** (Média Prioridade)

---

## 🐛 **Correções de Bugs**

- ✅ Pedidos de amizade agora são exibidos corretamente
- ✅ Configurações do Aria2 persistem entre sessões
- ✅ Interface responsiva em diferentes tamanhos de tela

---

## 📱 **Compatibilidade**

- ✅ Android 5.0+ (API 21+)
- ✅ Testado em dispositivos ARM e x86
- ✅ Suporte a Material You (Android 12+)

---

**Desenvolvido com Continue AI**
*PR: #7 - investigation/linux-proton-status-11603529385782947407*
