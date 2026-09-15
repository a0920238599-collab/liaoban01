package com.liaoban.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.liaoban.ai.ai.ChatEngine
import com.liaoban.ai.settings.AppSettings
import com.liaoban.ai.storage.AppDb
import com.liaoban.ai.storage.ChatMessage
import com.liaoban.ai.storage.Contact
import com.liaoban.ai.storage.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiaoBanApp() {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = "main"
    ) {
        composable("main") {
            MainTabs(
                openChat = { nav.navigate("chat/$it") },
                openSettings = { nav.navigate("contact/$it") }
            )
        }

        composable(
            "chat/{id}",
            arguments = listOf(navArgument("id") {
                type = NavType.IntType
            })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 1
            ChatScreen(
                contactId = id,
                onBack = { nav.popBackStack() },
                openSettings = { nav.navigate("contact/$it") }
            )
        }

        composable(
            "contact/{id}",
            arguments = listOf(navArgument("id") {
                type = NavType.IntType
            })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 1
            ContactSettingsScreen(
                contactId = id,
                onBack = { nav.popBackStack() },
                openMemories = { nav.navigate("memory/$it") }
            )
        }

        composable(
            "memory/{id}",
            arguments = listOf(navArgument("id") {
                type = NavType.IntType
            })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 1
            MemoryScreen(
                contactId = id,
                onBack = { nav.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainTabs(
    openChat: (Int) -> Unit,
    openSettings: (Int) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val labels = listOf("消息", "联系人", "AI设置", "我的")
                val icons = listOf("💬", "👥", "⚙", "☺")

                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(icons[index]) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ContactsScreen("消息", openChat, openSettings)
                1 -> ContactsScreen("联系人", openChat, openSettings)
                2 -> ApiSettingsScreen()
                else -> MeScreen()
            }
        }
    }
}

@Composable
private fun ContactsScreen(
    title: String,
    openChat: (Int) -> Unit,
    openSettings: (Int) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }

    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) {
            db.getContacts()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(20.dp)
        )

        LazyColumn {
            items(contacts, key = { it.id }) { contact ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openChat(contact.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                contact.name.take(1),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            contact.name,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            contact.subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (contact.model.isBlank())
                                "模型ID未设置"
                            else
                                contact.model,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    TextButton(
                        onClick = { openSettings(contact.id) }
                    ) {
                        Text("设置")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChatScreen(
    contactId: Int,
    onBack: () -> Unit,
    openSettings: (Int) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val engine = remember { ChatEngine(context) }
    val scope = rememberCoroutineScope()

    var contact by remember { mutableStateOf<Contact?>(null) }
    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var input by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun reload() {
        contact = db.getContact(contactId)
        messages = db.getMessages(contactId)
    }

    LaunchedEffect(contactId) {
        withContext(Dispatchers.IO) {
            val c = db.getContact(contactId)
            val m = db.getMessages(contactId)
            withContext(Dispatchers.Main) {
                contact = c
                messages = m
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 返回")
            }

            Column(Modifier.weight(1f)) {
                Text(
                    contact?.name ?: "聊天",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "AI 联系人",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            TextButton(
                onClick = { openSettings(contactId) }
            ) {
                Text("设置")
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "直接发消息。重要信息会逐步整理成长期记忆。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            if (sending) {
                item {
                    Text(
                        "${contact?.name ?: "对方"}正在输入…",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        errorText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("发消息") },
                maxLines = 5
            )

            Spacer(Modifier.width(8.dp))

            Button(
                enabled = input.isNotBlank() && !sending,
                onClick = {
                    val text = input
                    input = ""
                    sending = true
                    errorText = null

                    scope.launch {
                        runCatching {
                            engine.send(
                                contactId = contactId,
                                text = text,
                                onChanged = { reload() }
                            )
                        }.onFailure {
                            errorText = it.message ?: "发送失败"
                        }

                        sending = false
                        reload()

                        launch(Dispatchers.IO) {
                            runCatching {
                                engine.processMemory(contactId)
                            }
                        }
                    }
                }
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (isUser) Arrangement.End
            else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = if (isUser) 4.dp else 1.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                Modifier.padding(
                    horizontal = 13.dp,
                    vertical = 9.dp
                )
            ) {
                Text(message.text)
                Spacer(Modifier.height(3.dp))
                Text(
                    SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                    ).format(Date(message.createdAt)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ApiSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }

    var endpoint by remember { mutableStateOf(settings.endpoint) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var memoryModel by remember { mutableStateOf(settings.memoryModel) }
    var proactiveModel by remember { mutableStateOf(settings.proactiveModel) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "AI 设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "v2 默认通过 RunAPI 中转。为了不猜接口地址，请把 RunAPI 后台/文档给出的“完整 Chat Completions HTTPS 地址”原样粘贴进来。"
        )

        OutlinedTextField(
            value = endpoint,
            onValueChange = {
                endpoint = it
                saved = false
            },
            label = {
                Text("RunAPI 完整 Chat Completions 接口地址")
            },
            placeholder = {
                Text("https://…/v1/chat/completions")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = key,
            onValueChange = {
                key = it
                saved = false
            },
            label = { Text("RunAPI API Key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        OutlinedTextField(
            value = memoryModel,
            onValueChange = {
                memoryModel = it
                saved = false
            },
            label = { Text("记忆整理模型 ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = proactiveModel,
            onValueChange = {
                proactiveModel = it
                saved = false
            },
            label = { Text("主动聊天判断模型 ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                settings.endpoint = endpoint
                settings.apiKey = key
                settings.memoryModel = memoryModel
                settings.proactiveModel = proactiveModel
                saved = true
            }
        ) {
            Text("保存")
        }

        if (saved) {
            Text("已保存。API Key 使用 Android Keystore 加密保存。")
        }

        HorizontalDivider()

        Text(
            "联系人模型",
            fontWeight = FontWeight.SemiBold
        )
        Text("小夏默认：gpt-5.6-sol")
        Text("阿深默认：deepseek-v4-pro")
        Text(
            "Grok：因为 RunAPI 的实际 Grok 模型 ID 可能变化，v2 不乱写死。进入 G 的联系人设置，把 RunAPI 模型列表中的实际 ID 粘贴进去。"
        )

        Text(
            "如果以后 RunAPI 更换接口地址或模型名，只需要在手机里修改，不需要重新编译 APK。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ContactSettingsScreen(
    contactId: Int,
    onBack: () -> Unit,
    openMemories: (Int) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val scope = rememberCoroutineScope()

    var contact by remember { mutableStateOf<Contact?>(null) }
    var model by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var proactive by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        val c = withContext(Dispatchers.IO) {
            db.getContact(contactId)
        } ?: return@LaunchedEffect

        contact = c
        model = c.model
        persona = c.corePersona
        style = c.styleRules
        proactive = c.proactiveEnabled
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 返回")
            }

            Text(
                "${contact?.name ?: ""}设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "RunAPI 模型 ID",
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = model,
            onValueChange = {
                model = it
                saved = false
            },
            label = { Text("模型 ID") },
            placeholder = { Text("从 RunAPI 模型列表复制") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            "核心人格：只有你手动改才会改变，聊天不会自动重写。",
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = persona,
            onValueChange = {
                persona = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )

        Text(
            "表达习惯",
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = style,
            onValueChange = {
                style = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = proactive,
                onCheckedChange = {
                    proactive = it
                    saved = false
                }
            )

            Spacer(Modifier.width(8.dp))

            Text("允许在合适的时候主动发消息")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val old = contact ?: return@Button

                    scope.launch(Dispatchers.IO) {
                        val updated = old.copy(
                            model = model.trim(),
                            corePersona = persona.trim(),
                            styleRules = style.trim(),
                            proactiveEnabled = proactive
                        )
                        db.updateContact(updated)

                        withContext(Dispatchers.Main) {
                            contact = updated
                            saved = true
                        }
                    }
                }
            ) {
                Text("保存")
            }

            OutlinedButton(
                onClick = { openMemories(contactId) }
            ) {
                Text("查看记忆")
            }
        }

        if (saved) {
            Text("已保存。换模型不会删除联系人记忆。")
        }
    }
}

@Composable
private fun MemoryScreen(
    contactId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDb.get(context) }
    val scope = rememberCoroutineScope()

    var memories by remember {
        mutableStateOf(emptyList<MemoryItem>())
    }

    fun reload() {
        memories = db.getMemories(contactId, 250)
    }

    LaunchedEffect(contactId) {
        memories = withContext(Dispatchers.IO) {
            db.getMemories(contactId, 250)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 返回")
            }

            Text(
                "联系人记忆",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        db.clearMemories(contactId)
                        withContext(Dispatchers.Main) {
                            reload()
                        }
                    }
                }
            ) {
                Text("清空")
            }
        }

        Text(
            "这里是聊天中提炼出的长期记忆。核心人格不在这里，删除记忆不会改变联系人核心性格。",
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 6.dp
            ),
            style = MaterialTheme.typography.bodySmall
        )

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(memories, key = { it.id }) { memory ->
                Card {
                    Column(
                        Modifier.padding(12.dp)
                    ) {
                        Row {
                            Text(
                                memory.type,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.weight(1f))

                            Text(
                                "重要度 ${
                                    String.format(
                                        Locale.getDefault(),
                                        "%.2f",
                                        memory.importance
                                    )
                                }"
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(memory.content)

                        if (memory.tags.isNotBlank()) {
                            Text(
                                memory.tags,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    db.deleteMemory(memory.id)
                                    withContext(Dispatchers.Main) {
                                        reload()
                                    }
                                }
                            }
                        ) {
                            Text("删除这条记忆")
                        }
                    }
                }
            }

            if (memories.isEmpty()) {
                item {
                    Text("还没有形成长期记忆。默认每 6 条用户消息整理一次。")
                }
            }
        }
    }
}

@Composable
private fun MeScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "我的",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text("聊伴 AI · v0.2 RunAPI")
        Text("无登录，本机直接使用。")

        HorizontalDivider()

        Text("这一版：")
        Text("• RunAPI 统一中转")
        Text("• GPT / DeepSeek / Grok 可通过模型 ID 切换")
        Text("• 本地 SQLite 聊天记录")
        Text("• 固定核心人格 + 慢慢形成的关系")
        Text("• 长期记忆提炼 + 相关记忆召回")
        Text("• 最近消息 + 摘要，不重发全部历史")
        Text("• 一次回复 1~3 条，带自然间隔")
        Text("• 后台主动聊天判断")
        Text("• API Key 使用 Android Keystore 加密")

        Spacer(Modifier.height(8.dp))

        Text(
            "自然交流不等于冒充真人：联系人资料明确显示“AI 联系人”，但聊天节奏、记忆和表达尽量接近熟悉的即时通讯联系人。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
