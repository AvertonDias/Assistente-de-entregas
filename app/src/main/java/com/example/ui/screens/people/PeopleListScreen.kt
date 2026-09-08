package com.example.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.ui.components.DialogBlurEffect
import com.example.ui.components.VoiceInputIconButton
import com.example.ui.navigation.Screen
import com.example.util.AddressNormalizer
import com.example.util.ClipboardHelper
import com.example.util.FeedbackHelper
import com.example.util.SpeechHelper

private data class ReceiverDeleteTarget(
    val person: Person,
    val index: Int,
    val receiver: Recebedor
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    viewModel: PeopleViewModel,
    onNavigateBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val persons by viewModel.persons.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var personToDelete by remember { mutableStateOf<Person?>(null) }
    var receiverToDelete by remember { mutableStateOf<ReceiverDeleteTarget?>(null) }
    var filterOnlyMultipleReceivers by remember { mutableStateOf(false) }

    fun hasMultipleReceivers(person: Person): Boolean {
        if (person.coRecebedoresJson.isNotBlank()) {
            try {
                val extras = Recebedor.listFromJson(person.coRecebedoresJson)
                val validExtras = extras.count { it.nome.isNotBlank() || it.documento.isNotBlank() }
                val hasMain = person.nome.isNotBlank() || person.documento.isNotBlank()
                return ((if (hasMain) 1 else 0) + validExtras) > 1
            } catch (_: Throwable) {
                return true
            }
        }
        return false
    }

    val multipleCount = remember(persons) {
        persons.count { hasMultipleReceivers(it) }
    }

    val filteredPersons = remember(persons, filterOnlyMultipleReceivers) {
        if (!filterOnlyMultipleReceivers) {
            persons
        } else {
            persons.filter { hasMultipleReceivers(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Destinatários & Pessoas", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Screen.PersonEdit.createRoute(0)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_person_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Cadastrar Pessoa")
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .blur(if (personToDelete != null || receiverToDelete != null) 12.dp else 0.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideScreen = maxWidth >= 720.dp
            val contentMaxWidth = if (isWideScreen) 1100.dp else 650.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentMaxWidth)
            ) {
                // Campo de Pesquisa Inteligente
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (isWideScreen) 24.dp else 16.dp, end = if (isWideScreen) 24.dp else 16.dp, top = 12.dp, bottom = 6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Pesquisar por rua, número da rua ou nome...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                                    }
                                }
                                VoiceInputIconButton(
                                    hintPrompt = "Fale o nome, rua ou número...",
                                    onResult = { spoken ->
                                        val clean = SpeechHelper.processSpokenSearch(spoken)
                                        if (clean.isNotBlank()) {
                                            viewModel.onSearchQueryChanged(clean)
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_people_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        singleLine = true
                    )
                }

                // Seletor para mostrar apenas endereços com mais de um recebedor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (isWideScreen) 24.dp else 16.dp, end = if (isWideScreen) 24.dp else 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterOnlyMultipleReceivers,
                        onClick = { filterOnlyMultipleReceivers = !filterOnlyMultipleReceivers },
                        label = {
                            Text(
                                text = if (filterOnlyMultipleReceivers) {
                                    "Apenas com +1 recebedor ($multipleCount)"
                                } else {
                                    "Mais de 1 recebedor ($multipleCount)"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (filterOnlyMultipleReceivers) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("filter_multiple_receivers_chip")
                    )
                }

                if (filteredPersons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (filterOnlyMultipleReceivers) Icons.Default.People else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (filterOnlyMultipleReceivers) {
                                    "Nenhum endereço com múltiplos recebedores encontrado."
                                } else if (searchQuery.isBlank()) {
                                    "Nenhuma pessoa cadastrada ainda."
                                } else {
                                    "Nenhum resultado para \"$searchQuery\"."
                                },
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Layout Responsivo: Grade de 2 colunas para PC / telas largas e 1 coluna para celular
                    if (isWideScreen) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(span = { GridItemSpan(2) }) {
                                Text(
                                    text = if (filterOnlyMultipleReceivers) {
                                        "${filteredPersons.size} endereço(s) com mais de 1 recebedor"
                                    } else {
                                        "${filteredPersons.size} ${if (filteredPersons.size == 1) "endereço cadastrado" else "endereços cadastrados"}"
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            items(
                                items = filteredPersons,
                                key = { person -> if (person.id > 0) "p_${person.id}" else "p_${person.endereco}_${person.numero}_${person.nome}_${person.dataCriacao}" }
                            ) { person ->
                                PersonCardItem(
                                    person = person,
                                    onEdit = { onNavigate(Screen.PersonEdit.createRoute(person.id)) },
                                    onDelete = { personToDelete = person },
                                    onDeleteReceiver = { index, receiver ->
                                        receiverToDelete = ReceiverDeleteTarget(person, index, receiver)
                                    },
                                    onCopyName = { ClipboardHelper.copyToClipboard(context, "Nome", person.nome) },
                                    onCopyDoc = { ClipboardHelper.copyToClipboard(context, "Documento", person.documento) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = if (filterOnlyMultipleReceivers) {
                                        "${filteredPersons.size} endereço(s) com mais de 1 recebedor"
                                    } else {
                                        "${filteredPersons.size} ${if (filteredPersons.size == 1) "endereço cadastrado" else "endereços cadastrados"}"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            items(
                                items = filteredPersons,
                                key = { person -> if (person.id > 0) "p_${person.id}" else "p_${person.endereco}_${person.numero}_${person.nome}_${person.dataCriacao}" }
                            ) { person ->
                                PersonCardItem(
                                    person = person,
                                    onEdit = { onNavigate(Screen.PersonEdit.createRoute(person.id)) },
                                    onDelete = { personToDelete = person },
                                    onDeleteReceiver = { index, receiver ->
                                        receiverToDelete = ReceiverDeleteTarget(person, index, receiver)
                                    },
                                    onCopyName = { ClipboardHelper.copyToClipboard(context, "Nome", person.nome) },
                                    onCopyDoc = { ClipboardHelper.copyToClipboard(context, "Documento", person.documento) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Diálogo de Confirmação para Remoção de Recebedor Específico
        if (receiverToDelete != null) {
            val target = receiverToDelete!!
            val remainingCount = remember(target.person) {
                val list = mutableListOf<Recebedor>()
                if (target.person.nome.isNotBlank() || target.person.documento.isNotBlank()) {
                    list.add(Recebedor(id = "main", nome = target.person.nome, documento = target.person.documento, assinatura = target.person.assinatura))
                }
                if (target.person.coRecebedoresJson.isNotBlank()) {
                    try {
                        list.addAll(Recebedor.listFromJson(target.person.coRecebedoresJson))
                    } catch (_: Throwable) {}
                }
                (list.size - 1).coerceAtLeast(0)
            }

            AlertDialog(
                onDismissRequest = { receiverToDelete = null },
                title = {
                    DialogBlurEffect()
                    Text("Remover Recebedor", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Deseja realmente remover este recebedor do endereço?")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "👤 ${target.receiver.nome.ifBlank { "Sem nome" }}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (target.receiver.documento.isNotBlank()) {
                                    Text(
                                        text = "📄 Doc: ${target.receiver.documento}",
                                        fontSize = 12.5.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = "📍 ${target.person.endereco}, Nº ${target.person.numero}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "✓ O endereço e os demais $remainingCount recebedor(es) continuarão cadastrados normalmente.",
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeReceiverFromPerson(target.person, target.index)
                            receiverToDelete = null
                            FeedbackHelper.triggerSuccess(context)
                            Toast.makeText(context, "Recebedor removido com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("REMOVER", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { receiverToDelete = null }) {
                        Text("CANCELAR")
                    }
                }
            )
        }

        // Diálogo de Confirmação de Exclusão de Cadastro / Endereço Completo
        if (personToDelete != null) {
            val target = personToDelete!!
            val targetReceivers = remember(target) {
                val list = mutableListOf<Recebedor>()
                if (target.nome.isNotBlank() || target.documento.isNotBlank()) {
                    list.add(Recebedor(id = "main", nome = target.nome, documento = target.documento, assinatura = target.assinatura))
                }
                if (target.coRecebedoresJson.isNotBlank()) {
                    try {
                        list.addAll(Recebedor.listFromJson(target.coRecebedoresJson))
                    } catch (_: Throwable) {}
                }
                list
            }
            val isMultiple = targetReceivers.size > 1

            AlertDialog(
                onDismissRequest = { personToDelete = null },
                title = {
                    DialogBlurEffect()
                    Text(if (isMultiple) "Excluir Endereço Completo?" else "Excluir Cadastro", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isMultiple) {
                            Text(
                                text = "Atenção: este endereço possui ${targetReceivers.size} recebedores cadastrados:",
                                fontWeight = FontWeight.SemiBold
                            )
                            targetReceivers.forEach { r ->
                                Text(
                                    text = "• ${r.nome.ifBlank { "Sem nome" }}${if (r.documento.isNotBlank()) " (Doc: ${r.documento})" else ""}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Excluir aqui apagará o ENDEREÇO e TODOS os ${targetReceivers.size} recebedores.\n\n💡 Dica: para remover apenas um recebedor, clique no botão \"Remover\" ao lado do nome dele no cartão.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("Deseja realmente excluir o cadastro de ${target.nome.ifBlank { "Destinatário" }} (${target.endereco}, Nº ${target.numero})? Esta ação não pode ser desfeita.")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePerson(target)
                            personToDelete = null
                            FeedbackHelper.triggerSuccess(context)
                            Toast.makeText(context, "Cadastro excluído!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(if (isMultiple) "EXCLUIR TUDO" else "EXCLUIR", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { personToDelete = null }) {
                        Text("CANCELAR")
                    }
                }
            )
        }
    }
}

@Composable
private fun PersonCardItem(
    person: Person,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteReceiver: (Int, Recebedor) -> Unit,
    onCopyName: () -> Unit,
    onCopyDoc: () -> Unit
) {
    val context = LocalContext.current
    val parsedAddress = remember(person.endereco, person.numero, person.complemento, person.bairro) {
        AddressNormalizer.parseAddressComponents(
            person.endereco,
            person.numero,
            person.complemento,
            person.bairro
        )
    }

    val allRecebedores = remember(person) {
        val list = mutableListOf<Recebedor>()
        if (person.nome.isNotBlank() || person.documento.isNotBlank()) {
            list.add(Recebedor(id = "main", nome = person.nome, documento = person.documento, assinatura = person.assinatura))
        }
        if (person.coRecebedoresJson.isNotBlank()) {
            try {
                list.addAll(Recebedor.listFromJson(person.coRecebedoresJson))
            } catch (_: Throwable) {}
        }
        list
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person_item_${person.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // TÍTULO PRINCIPAL: ENDEREÇO COM DESTAQUE PARA NÚMERO E UNIDADE (APTO / BLOCO / CASA)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = parsedAddress.street.ifBlank { "Sem logradouro" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // BADGES DE DESTAQUE: NÚMERO E COMPLEMENTO / UNIDADE
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 26.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (parsedAddress.number.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                )
                            ) {
                                Text(
                                    text = "Nº ${parsedAddress.number}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (parsedAddress.hasComplement) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                                )
                            ) {
                                Text(
                                    text = parsedAddress.unitBadgeText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (parsedAddress.neighborhood.isNotBlank()) {
                            Text(
                                text = "• ${parsedAddress.neighborhood}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // LISTA DE TODOS OS DESTINATÁRIOS / MORADORES CADASTRADOS NESTE ENDEREÇO
            if (allRecebedores.isNotEmpty()) {
                if (allRecebedores.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${allRecebedores.size} moradores/destinatários cadastrados:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allRecebedores.forEachIndexed { index, rec ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (allRecebedores.size > 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(if (allRecebedores.size > 1) 8.dp else 0.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (index == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = rec.nome.ifBlank { "Sem nome cadastrado" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                    }

                                    // Indicador de assinatura
                                    if (rec.assinatura.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFE8F5E9)
                                        ) {
                                            Text(
                                                text = "✓ Assinado",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (rec.documento.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Doc: ${rec.documento}",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 22.dp)
                                    )
                                }

                                // Botões de copiar para este recebedor específico
                                if (allRecebedores.size > 1) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 22.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (rec.nome.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.clickable {
                                                    ClipboardHelper.copyToClipboard(context, "Nome", rec.nome)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Copiar Nome", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                        if (rec.documento.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.clickable {
                                                    ClipboardHelper.copyToClipboard(context, "Documento", rec.documento)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Copiar Doc", fontSize = 10.sp)
                                                }
                                            }
                                        }

                                        // Botão de remover este recebedor específico
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.clickable {
                                                onDeleteReceiver(index, rec)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remover recebedor",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Remover",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sem destinatário cadastrado",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // BOTÕES DE AÇÃO RÁPIDA GERAIS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allRecebedores.size <= 1) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCopyName() }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar Nome", fontSize = 11.sp)
                        }
                    }

                    if (person.documento.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onCopyDoc() }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar Doc", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (parsedAddress.street.isNotBlank() || parsedAddress.number.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                ClipboardHelper.copyToClipboard(context, "Endereço", parsedAddress.fullDisplay)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar End", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
