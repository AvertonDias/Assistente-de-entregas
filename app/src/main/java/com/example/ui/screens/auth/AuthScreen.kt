package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoBackgroundLight
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoOnPrimaryContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.BentoPrimaryDark
import com.example.ui.theme.BentoSurfaceCard
import com.example.ui.theme.BentoSurfaceLight
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ErrorRedContainer
import com.example.ui.theme.SuccessGreen

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    onContinueOffline: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Redireciona imediatamente se já houver sessão ativa
    LaunchedEffect(uiState.currentUser) {
        if (uiState.currentUser != null) {
            onAuthSuccess()
        }
    }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoBackgroundLight)
            .testTag("auth_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Hero Bento
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_header_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = BentoPrimaryContainer),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE)))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(BentoPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📦", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Assistente de Entregas",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BentoPrimaryDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.isRegisterMode) "Crie sua conta para sincronizar na nuvem" else "Acesse suas rotas e preenchimentos automáticos",
                        fontSize = 13.sp,
                        color = BentoOnPrimaryContainer.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Formulário Principal
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_form_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoSurfaceLight),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Seletor de Modo: Entrar / Cadastrar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BentoSurfaceCard, RoundedCornerShape(16.dp))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!uiState.isRegisterMode) Color.White else Color.Transparent)
                                .clickable { if (uiState.isRegisterMode) viewModel.toggleMode() }
                                .padding(vertical = 10.dp)
                                .testTag("tab_login"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Entrar",
                                fontSize = 14.sp,
                                fontWeight = if (!uiState.isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (!uiState.isRegisterMode) BentoPrimaryDark else BentoTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (uiState.isRegisterMode) Color.White else Color.Transparent)
                                .clickable { if (!uiState.isRegisterMode) viewModel.toggleMode() }
                                .padding(vertical = 10.dp)
                                .testTag("tab_register"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Criar Conta",
                                fontSize = 14.sp,
                                fontWeight = if (uiState.isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (uiState.isRegisterMode) BentoPrimaryDark else BentoTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mensagem de Erro
                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        uiState.errorMessage?.let { error ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = ErrorRedContainer)
                            ) {
                                Text(
                                    text = error,
                                    fontSize = 13.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    // Cores de alto contraste e legibilidade com texto preto
                    val authFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedLabelColor = BentoPrimary,
                        unfocusedLabelColor = Color(0xFF334155),
                        focusedPlaceholderColor = Color(0xFF64748B),
                        unfocusedPlaceholderColor = Color(0xFF64748B),
                        focusedBorderColor = BentoPrimary,
                        unfocusedBorderColor = Color(0xFF94A3B8),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedLeadingIconColor = BentoPrimary,
                        unfocusedLeadingIconColor = Color(0xFF475569),
                        focusedTrailingIconColor = BentoPrimary,
                        unfocusedTrailingIconColor = Color(0xFF475569),
                        cursorColor = BentoPrimary
                    )

                    // Campo Nome (apenas Cadastro)
                    if (uiState.isRegisterMode) {
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = { viewModel.updateName(it) },
                            label = { Text("Nome completo", fontWeight = FontWeight.Medium) },
                            placeholder = { Text("Digite seu nome") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_name"),
                            colors = authFieldColors
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Campo E-mail
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.updateEmail(it) },
                        label = { Text("E-mail", fontWeight = FontWeight.Medium) },
                        placeholder = { Text("exemplo@email.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_email"),
                        colors = authFieldColors
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Campo Senha
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Senha", fontWeight = FontWeight.Medium) },
                        placeholder = { Text("Digite sua senha") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (uiState.isRegisterMode) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (!uiState.isRegisterMode) {
                                    viewModel.signInWithEmail { onAuthSuccess() }
                                }
                            }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_password"),
                        colors = authFieldColors
                    )

                    // Campo Confirmar Senha (apenas Cadastro)
                    if (uiState.isRegisterMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.confirmPassword,
                            onValueChange = { viewModel.updateConfirmPassword(it) },
                            label = { Text("Confirmar senha", fontWeight = FontWeight.Medium) },
                            placeholder = { Text("Repita sua senha") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.signUpWithEmail { onAuthSuccess() }
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_confirm_password"),
                            colors = authFieldColors
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Botão Principal (Entrar ou Cadastrar)
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (uiState.isRegisterMode) {
                                viewModel.signUpWithEmail { onAuthSuccess() }
                            } else {
                                viewModel.signInWithEmail { onAuthSuccess() }
                            }
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_submit_auth"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = if (uiState.isRegisterMode) "Cadastrar Conta" else "Entrar no Aplicativo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Divisor Ou
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BentoBorder)
                        Text(
                            text = "ou continue com",
                            fontSize = 12.sp,
                            color = BentoTextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BentoBorder)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Botão de Login com Google estilizado e responsivo
                    Surface(
                        onClick = {
                            if (!uiState.isLoading) {
                                viewModel.signInWithGoogle(context) { onAuthSuccess() }
                            }
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, BentoBorder),
                        shadowElevation = if (uiState.isLoading) 0.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_google_signin")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Logo autêntico do Google desenhado em Canvas
                            GoogleLogoCanvas(modifier = Modifier.size(20.dp))

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "Continuar com o Google",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BentoTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Opção Convidado / Continuar Offline
            TextButton(
                onClick = onContinueOffline,
                modifier = Modifier.testTag("btn_continue_offline")
            ) {
                Text(
                    text = "Continuar sem login (Modo Offline Local)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = BentoTextSecondary
                )
            }
        }
    }
}

@Composable
fun GoogleLogoCanvas(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = width.coerceAtMost(height) / 2f
        val center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f)

        val blue = Color(0xFF4285F4)
        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)

        val strokeWidth = radius * 0.42f
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2 - strokeWidth, radius * 2 - strokeWidth)
        val arcTopLeft = androidx.compose.ui.geometry.Offset(center.x - radius + strokeWidth / 2, center.y - radius + strokeWidth / 2)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)

        drawArc(
            color = blue,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = green,
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = yellow,
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = red,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke
        )

        val barHeight = strokeWidth * 0.95f
        drawRect(
            color = blue,
            topLeft = androidx.compose.ui.geometry.Offset(center.x - strokeWidth * 0.1f, center.y - barHeight / 2),
            size = androidx.compose.ui.geometry.Size(radius - strokeWidth * 0.05f, barHeight)
        )
    }
}
