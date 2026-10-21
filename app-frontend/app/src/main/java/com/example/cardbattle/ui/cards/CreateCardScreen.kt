package com.example.cardbattle.ui.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardbattle.data.remote.CardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

private data class ArtStroke(
    val color: Color,
    val points: List<Offset>,
)

private data class BrushColor(
    val label: String,
    val color: Color,
)

@Composable
fun CreateCardScreen(
    onBack: () -> Unit,
    onSaveLocal: suspend (
        id: String,
        name: String,
        type: CardType,
        pngBytes: ByteArray,
    ) -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<CardType?>(null) }
    var classMenuOpen by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Color.Black) }

    val strokes = remember { mutableStateListOf<ArtStroke>() }
    val scope = rememberCoroutineScope()
    val draftId = remember { UUID.randomUUID().toString() }

    var exporting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var previewPng by remember { mutableStateOf<ByteArray?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val busy = exporting || saving

    val cardColor = when (selectedType) {
        CardType.WARRIOR -> Color(0xFF963D45)
        CardType.BRAWLER -> Color(0xFF925027)
        CardType.RANGER -> Color(0xFF326448)
        CardType.MAGE -> Color(0xFF69469B)
        CardType.ROGUE -> Color(0xFF364C68)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }

    val cardTextColor = if (selectedType == null) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }

    val palette = remember {
        listOf(
            BrushColor("Black", Color.Black),
            BrushColor("Red", Color(0xFFD32F2F)),
            BrushColor("Blue", Color(0xFF1976D2)),
            BrushColor("Green", Color(0xFF388E3C)),
            BrushColor("Yellow", Color(0xFFFBC02D)),
            BrushColor("White", Color.White),
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = !busy,
                ) {
                    Text("Back")
                }

                Text(
                    text = "Create a card",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (!started) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { started = true },
                            modifier = Modifier
                                .size(96.dp)
                                .semantics {
                                    contentDescription = "Start a new card"
                                },
                            shape = CircleShape,
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.displayMedium,
                            )
                        }

                        Text("Make something worth battling with.")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardColor,
                            contentColor = cardTextColor,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    if (it.length <= 150) {
                                        name = it
                                        successMessage = null
                                    }
                                },
                                enabled = !busy,
                                label = { Text("Card name") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge
                                    .copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = cardTextColor,
                                    unfocusedTextColor = cardTextColor,
                                    focusedLabelColor = cardTextColor,
                                    unfocusedLabelColor = cardTextColor,
                                    focusedBorderColor = cardTextColor,
                                    unfocusedBorderColor =
                                        cardTextColor.copy(alpha = 0.6f),
                                    cursorColor = cardTextColor,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            DrawingPad(
                                strokes = strokes,
                                color = selectedColor,
                                enabled = !busy,
                                onDrawingChanged = {
                                    successMessage = null
                                },
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { classMenuOpen = true },
                                    enabled = !busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 88.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = cardTextColor
                                    ),
                                ) {
                                    Column(
                                        horizontalAlignment =
                                            Alignment.CenterHorizontally,
                                        verticalArrangement =
                                            Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = selectedType?.label
                                                ?: "Choose a class",
                                            style = MaterialTheme.typography
                                                .headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )

                                        Text(
                                            text = "Tap to change ▾",
                                            style = MaterialTheme.typography
                                                .bodySmall,
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = classMenuOpen,
                                    onDismissRequest = {
                                        classMenuOpen = false
                                    },
                                ) {
                                    CardType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.label) },
                                            onClick = {
                                                selectedType = type
                                                classMenuOpen = false
                                                successMessage = null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Draw directly on the white art area",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        palette.forEach { brush ->
                            Surface(
                                onClick = { selectedColor = brush.color },
                                enabled = !busy,
                                shape = CircleShape,
                                color = brush.color,
                                modifier = Modifier
                                    .size(40.dp)
                                    .semantics {
                                        contentDescription =
                                            "${brush.label} brush"
                                    }
                                    .border(
                                        width = if (
                                            selectedColor == brush.color
                                        ) 3.dp else 1.dp,
                                        color = if (
                                            selectedColor == brush.color
                                        ) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Gray
                                        },
                                        shape = CircleShape,
                                    ),
                            ) {}
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            enabled = strokes.isNotEmpty() && !busy,
                            onClick = {
                                strokes.removeAt(strokes.lastIndex)
                                successMessage = null
                            },
                        ) {
                            Text("Undo")
                        }

                        TextButton(
                            enabled = strokes.isNotEmpty() && !busy,
                            onClick = {
                                strokes.clear()
                                successMessage = null
                            },
                        ) {
                            Text("Clear art")
                        }
                    }

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }

                    successMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Button(
                        enabled = name.isNotBlank() &&
                                selectedType != null &&
                                !busy,
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(),
                        onClick = {
                            val drawing = strokes.toList()
                            exporting = true
                            errorMessage = null
                            saveError = null

                            scope.launch {
                                try {
                                    previewPng = withContext(
                                        Dispatchers.Default
                                    ) {
                                        exportDrawing(drawing)
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    errorMessage =
                                        "Could not prepare the preview. Try again."
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                    ) {
                        Text(
                            if (exporting) "Preparing…" else "Preview card"
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    previewPng?.let { png ->
        val image = remember(png) {
            BitmapFactory.decodeByteArray(png, 0, png.size)
                .asImageBitmap()
        }

        AlertDialog(
            onDismissRequest = {
                if (!saving) {
                    previewPng = null
                    saveError = null
                }
            },
            containerColor = cardColor,
            titleContentColor = cardTextColor,
            textContentColor = cardTextColor,
            title = {
                Text(
                    text = name.trim(),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = "Your finished card art",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                    )

                    Text(
                        text = selectedType?.label.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    saveError?.let {
                        Text(
                            text = it,
                            color = cardTextColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = cardTextColor
                    ),
                    onClick = {
                        if (!saving) {
                            val type = checkNotNull(selectedType)
                            val cardName = name.trim()

                            saving = true
                            saveError = null

                            scope.launch {
                                try {
                                    onSaveLocal(
                                        draftId,
                                        cardName,
                                        type,
                                        png,
                                    )

                                    previewPng = null
                                    successMessage =
                                        "Card saved on this device!"
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    saveError =
                                        "Could not save the card. Please try again."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                ) {
                    Text(if (saving) "Saving…" else "Save locally")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        previewPng = null
                        saveError = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = cardTextColor
                    ),
                ) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun DrawingPad(
    strokes: SnapshotStateList<ArtStroke>,
    color: Color,
    enabled: Boolean,
    onDrawingChanged: () -> Unit,
) {
    val notifyDrawingChanged by rememberUpdatedState(onDrawingChanged)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = "Card art drawing area"
            }
            .pointerInput(color, enabled) {
                if (enabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false
                        )
                        down.consume()
                        notifyDrawingChanged()

                        fun normalize(position: Offset) = Offset(
                            (position.x / size.width.coerceAtLeast(1))
                                .coerceIn(0f, 1f),
                            (position.y / size.height.coerceAtLeast(1))
                                .coerceIn(0f, 1f),
                        )

                        var stroke = ArtStroke(
                            color = color,
                            points = listOf(normalize(down.position)),
                        )

                        strokes.add(stroke)
                        val index = strokes.lastIndex

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break

                            stroke = stroke.copy(
                                points = stroke.points +
                                        normalize(change.position)
                            )

                            strokes[index] = stroke
                            change.consume()
                        } while (change.pressed)
                    }
                }
            },
    ) {
        val brushWidth = size.minDimension * 0.012f

        strokes.forEach { stroke ->
            val points = stroke.points.map {
                Offset(it.x * size.width, it.y * size.height)
            }

            points.firstOrNull()?.let {
                drawCircle(
                    color = stroke.color,
                    radius = brushWidth / 2,
                    center = it,
                )
            }

            points.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = stroke.color,
                    start = start,
                    end = end,
                    strokeWidth = brushWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun exportDrawing(strokes: List<ArtStroke>): ByteArray {
    val side = 512
    val bitmap = Bitmap.createBitmap(
        side,
        side,
        Bitmap.Config.ARGB_8888,
    )

    try {
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = side * 0.012f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            paint.color = stroke.color.toArgb()

            stroke.points.firstOrNull()?.let {
                canvas.drawCircle(
                    it.x * side,
                    it.y * side,
                    paint.strokeWidth / 2,
                    paint,
                )
            }

            stroke.points.zipWithNext().forEach { (start, end) ->
                canvas.drawLine(
                    start.x * side,
                    start.y * side,
                    end.x * side,
                    end.y * side,
                    paint,
                )
            }
        }

        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}