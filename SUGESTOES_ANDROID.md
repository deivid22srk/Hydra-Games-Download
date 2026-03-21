# Sugestões de Funcionalidades do HydraPc para Adicionar ao Android

Baseado na investigação do **HydraPc** (versão desktop do Hydra Launcher), este documento lista funcionalidades que podem enriquecer significativamente a versão Android.

---

## 🎮 1. **Rastreamento Automático de Tempo de Jogo**

### **Status no HydraPc:**
✅ **Implementado e funcional**
- O HydraPc monitora processos ativos no sistema através do `process-watcher.ts`
- Usa Python RPC (`psutil`) para detectar jogos em execução
- Suporta detecção de jogos Windows nativos e via Proton/Wine no Linux
- Atualiza automaticamente o tempo de jogo e sincroniza com a API Hydra a cada 3 minutos
- Exibe status "Jogando [Nome do Jogo]" no perfil em tempo real

### **Status no Android:**
❌ **Não implementado**
- A versão Android **adiciona jogos à biblioteca manualmente**, mas não rastreia tempo de jogo automaticamente
- O tempo de jogo vem apenas da API Hydra (sincronizado de outras plataformas)

### **Sugestão de Implementação:**

#### **Opção 1: Rastreamento via Android Services (Recomendado)**
```kotlin
// Arquivo: core/main/src/main/java/com/rk/terminal/ui/services/GamePlaytimeTracker.kt
class GamePlaytimeTracker : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 60_000L // 1 minuto
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startTracking()
        return START_STICKY
    }
    
    private fun startTracking() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                checkRunningGames()
                handler.postDelayed(this, updateInterval)
            }
        }, updateInterval)
    }
    
    private fun checkRunningGames() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.getRunningTasks(10)
        
        // Verificar se algum jogo da biblioteca está rodando
        val libraryGames = getLibraryGames() // Da API Hydra
        
        runningTasks.forEach { task ->
            val packageName = task.topActivity?.packageName
            val matchedGame = libraryGames.find { it.packageName == packageName }
            
            if (matchedGame != null) {
                updatePlaytime(matchedGame, updateInterval)
            }
        }
    }
}
```

#### **Opção 2: Integração com UsageStatsManager**
```kotlin
// Melhor para Android 5.0+
val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
val currentTime = System.currentTimeMillis()
val usageStats = usageStatsManager.queryUsageStats(
    UsageStatsManager.INTERVAL_DAILY,
    currentTime - 1000 * 60 * 60 * 24, // Últimas 24h
    currentTime
)

// Filtrar apenas jogos da biblioteca Hydra
val gameStats = usageStats.filter { stat ->
    libraryGames.any { game -> game.packageName == stat.packageName }
}
```

### **Benefícios:**
- ✅ Sincronização automática de tempo de jogo entre PC e Android
- ✅ Status "Online" preciso no perfil Hydra
- ✅ Estatísticas de uso mais precisas
- ✅ Conquistas baseadas em tempo podem ser desbloqueadas

---

## 📊 2. **Sistema de Status "Jogando Agora" em Tempo Real**

### **Status no HydraPc:**
✅ **Implementado**
- Mostra "Jogando [Game]" no perfil quando um jogo está ativo
- Sincroniza via WebSocket para amigos verem em tempo real
- Exibido no `ProfileHero.tsx` com duração da sessão

### **Status no Android:**
❌ **Parcialmente implementado**
- O `ProfileScreen.kt` exibe `recentGames`, mas não mostra "Jogando agora"
- Não há indicação visual de status online/jogando

### **Sugestão de Implementação:**

#### **Adicionar campo `currentGame` ao ProfileScreen.kt:**
```kotlin
// Em ProfileScreen.kt, adicionar após linha 113 (após bio):

profile?.currentGame?.let { currentGame ->
    Spacer(modifier = Modifier.height(16.dp))
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Gamepad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Jogando agora",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = currentGame.title ?: "Jogo desconhecido",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "há ${formatSessionTime(currentGame.sessionDurationInSeconds ?: 0L)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}
```

#### **Atualizar HydraModels.kt:**
```kotlin
// Adicionar ao HydraProfile:
data class HydraProfile(
    // ... campos existentes ...
    @SerializedName("currentGame") val currentGame: HydraCurrentGame? = null
)

data class HydraCurrentGame(
    @SerializedName("title") val title: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("sessionDurationInSeconds") val sessionDurationInSeconds: Long? = null,
    @SerializedName("shop") val shop: String? = null,
    @SerializedName("objectId") val objectId: String? = null
)
```

---

## 🏆 3. **Sistema de Conquistas (Achievements)**

### **Status no HydraPc:**
✅ **Totalmente implementado**
- Rastreamento automático de conquistas via `AchievementWatcherManager`
- Sincronização bidirecional com API Hydra
- Notificações ao desbloquear conquistas
- Visualização de progresso e comparação com amigos

### **Status no Android:**
❌ **Não implementado**

### **Sugestão de Implementação:**

#### **Criar AchievementsScreen.kt:**
```kotlin
@Composable
fun AchievementsScreen(gameId: String, shop: String) {
    var achievements by remember { mutableStateOf<List<HydraAchievement>>(emptyList()) }
    
    LaunchedEffect(gameId) {
        achievements = fetchAchievements(gameId, shop)
    }
    
    LazyColumn {
        items(achievements) { achievement ->
            AchievementCard(
                name = achievement.displayName,
                description = achievement.description,
                isUnlocked = achievement.unlocked,
                iconUrl = achievement.iconUrl,
                unlockedAt = achievement.unlockedAt,
                percentage = achievement.percentage
            )
        }
    }
}
```

---

## ☁️ 4. **Sincronização de Save Games na Nuvem (Cloud Sync)**

### **Status no HydraPc:**
✅ **Implementado**
- Upload/download automático de saves via API Hydra
- Suporte a Ludusavi para mapeamento de saves
- Sincronização ao abrir/fechar jogos

### **Status no Android:**
❌ **Não implementado**

### **Sugestão:**
- **Baixa prioridade para Android**, pois a maioria dos jogos Android usa Google Play Games para saves
- Pode ser útil para jogos PC executados via Wine/Box86/Termux

---

## 👥 5. **Sistema de Amigos com Recursos Avançados**

### **Status no HydraPc:**
✅ **Muito completo**
- Ver o que amigos estão jogando em tempo real
- Comparar conquistas e estatísticas
- Sistema de mensagens (planejado)
- Notificações de atividade

### **Status no Android:**
✅ **Parcialmente implementado**
- Adicionar amigos por ID ✅
- Visualizar lista de amigos ✅
- Ver perfis de amigos ✅
- **Falta:** Status "Jogando agora" dos amigos, notificações de conquistas

### **Sugestão de Melhoria:**

#### **Adicionar FriendsActivityScreen.kt:**
```kotlin
@Composable
fun FriendsActivityScreen() {
    LazyColumn {
        items(friendsActivities) { activity ->
            when (activity.type) {
                "ACHIEVEMENT" -> AchievementActivityCard(activity)
                "NEW_GAME" -> NewGameActivityCard(activity)
                "PLAYING" -> PlayingNowCard(activity)
            }
        }
    }
}
```

---

## 🔔 6. **Sistema de Notificações**

### **Status no HydraPc:**
✅ **Implementado**
- Notificações de conquistas desbloqueadas
- Alertas de atualizações disponíveis
- Notificações de atividade de amigos

### **Status no Android:**
❌ **Não implementado**

### **Sugestão:**
```kotlin
// NotificationManager.kt
object HydraNotificationManager {
    fun showAchievementUnlocked(context: Context, achievement: HydraAchievement) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle("Conquista Desbloqueada!")
            .setContentText(achievement.displayName)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(achievement.id.hashCode(), notification)
    }
}
```

---

## 📈 7. **Estatísticas Detalhadas e Gráficos**

### **Status no HydraPc:**
✅ **Rico em estatísticas**
- Tempo de jogo por semana/mês
- Comparação com percentil global
- Gráficos de atividade

### **Status no Android:**
✅ **Básico implementado**
- Mostra tempo total, conquistas, karma
- **Falta:** Gráficos, comparações, histórico

### **Sugestão:**
- Adicionar biblioteca `MPAndroidChart` para gráficos
- Criar `StatsDetailScreen.kt` com gráficos de tempo de jogo

---

## 🎨 8. **Personalização de Perfil Avançada**

### **Status no HydraPc:**
✅ **Completo**
- Upload de imagem de perfil e background
- Edição de bio com formatação HTML
- Badges e conquistas exibidas

### **Status no Android:**
✅ **Implementado**
- Upload de imagens ✅
- Edição de bio ✅
- Badges exibidos ✅

---

## 📋 **Resumo de Prioridades**

| Funcionalidade | Prioridade | Complexidade | Impacto |
|---------------|-----------|--------------|---------|
| Rastreamento de Tempo de Jogo | 🔴 **Alta** | Média | Alto |
| Status "Jogando Agora" | 🔴 **Alta** | Baixa | Alto |
| Sistema de Conquistas | 🟡 **Média** | Alta | Alto |
| Notificações | 🟡 **Média** | Baixa | Médio |
| Atividade de Amigos | 🟢 **Baixa** | Média | Médio |
| Cloud Sync de Saves | 🟢 **Baixa** | Alta | Baixo (Android) |
| Estatísticas Avançadas | 🟡 **Média** | Média | Médio |

---

## 🛠️ **Próximos Passos Recomendados**

1. **Implementar rastreamento de tempo de jogo** usando `UsageStatsManager`
2. **Adicionar campo `currentGame`** ao modelo de perfil
3. **Criar sistema de notificações** para conquistas e eventos
4. **Desenvolver tela de conquistas** integrada à API Hydra

---

## 📚 **Referências de Código**

### HydraPc (Desktop):
- `src/main/services/process-watcher.ts` - Rastreamento de jogos
- `src/main/services/achievements/achievement-watcher-manager.ts` - Conquistas
- `src/renderer/src/pages/profile/profile-hero/profile-hero.tsx` - UI do perfil

### Android (Atual):
- `core/main/src/main/java/com/rk/terminal/ui/screens/home/ProfileScreen.kt` - Tela de perfil
- `core/main/src/main/java/com/rk/terminal/ui/screens/home/GameDetailsScreen.kt` - Detalhes do jogo
- `core/main/src/main/java/com/rk/terminal/ui/screens/home/HydraModels.kt` - Modelos de dados

---

**Nota:** Este documento foi gerado com base na análise do código-fonte do HydraPc e da versão Android do Hydra Launcher em 21/03/2026.
