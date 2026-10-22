package com.example.cardbattle.ui.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.example.cardbattle.data.remote.CardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import com.example.cardbattle.data.remote.CardResponse
import com.example.cardbattle.data.remote.NewCardSaveState

data class EditorStroke(
    val color: Color,
    val points: List<Offset>,
)

class CardEditorDraft(
    val cardId: Long? = null,
) {
    val isEditing: Boolean
        get() = cardId != null

    val started = mutableStateOf(isEditing)
    val name = mutableStateOf("")
    val selectedType = mutableStateOf<CardType?>(null)
    val brushColor = mutableStateOf(Color.Black)
    val basePng = mutableStateOf<ByteArray?>(null)
    val strokes = mutableStateListOf<EditorStroke>()


    val saveState = NewCardSaveState()

    companion object {
        fun fromCard(
            card: CardResponse,
            pngBytes: ByteArray?,
        ): CardEditorDraft =
            CardEditorDraft(cardId = card.id).apply {
                name.value = card.name
                selectedType.value = card.type
                basePng.value = pngBytes
            }
    }
}

class CardDraftsViewModel : ViewModel() {
    var newDraft by mutableStateOf(CardEditorDraft())
        private set

    val editDrafts = mutableStateMapOf<Long, CardEditorDraft>()

    private var ownerId: Int? = null

    fun selectUser(userId: Int?) {
        if (ownerId != userId) {
            clear()
            ownerId = userId
        }
    }

    fun openEdit(
        card: CardResponse,
        pngBytes: ByteArray?,
    ) {
        editDrafts.getOrPut(card.id) {
            CardEditorDraft.fromCard(card, pngBytes)
        }
    }

    fun finishNewCard() {
        newDraft = CardEditorDraft()
    }

    fun clear() {
        newDraft = CardEditorDraft()
        editDrafts.clear()
        ownerId = null
    }
}

@Composable
fun CreateCardScreen(
    draft: CardEditorDraft,
    onBack: () -> Unit,
    onSave: suspend (
        name: String,
        type: CardType,
        pngBytes: ByteArray,
    ) -> Unit,
) {
    var started by draft.started
    var name by draft.name
    var selectedType by draft.selectedType
    var brushColor by draft.brushColor
    var basePng by draft.basePng
    val strokes = draft.strokes

    val scope = rememberCoroutineScope()
    var classMenuOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    BackHandler(enabled = busy) {
        // Hopefully will keep the screen open while a save is running
    }
    var previewPng by remember { mutableStateOf<ByteArray?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val baseImage = remember(basePng) {
        basePng?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)
                ?.asImageBitmap()
        }
    }

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

    val palette = listOf(
        "Black" to Color.Black,
        "Red" to Color(0xFFD32F2F),
        "Blue" to Color(0xFF1976D2),
        "Green" to Color(0xFF388E3C),
        "Yellow" to Color(0xFFFBC02D),
        "White" to Color.White,
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    text = if (draft.isEditing) {
                        "Edit card"
                    } else {
                        "Create a card"
                    },
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
                                "+",
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
                                    if (it.length <= 150) name = it
                                },
                                enabled = !busy,
                                label = { Text("Card name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
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
                            )

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(
                                        1.dp,
                                        Color.Gray,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .semantics {
                                        contentDescription =
                                            "Card art drawing area"
                                    }
                                    .pointerInput(draft, brushColor, busy) {
                                        if (!busy) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(
                                                    requireUnconsumed = false
                                                )
                                                down.consume()

                                                fun normalize(p: Offset) =
                                                    Offset(
                                                        (p.x / size.width
                                                            .coerceAtLeast(1))
                                                            .coerceIn(0f, 1f),
                                                        (p.y / size.height
                                                            .coerceAtLeast(1))
                                                            .coerceIn(0f, 1f),
                                                    )

                                                var stroke = EditorStroke(
                                                    brushColor,
                                                    listOf(
                                                        normalize(down.position)
                                                    ),
                                                )
                                                strokes.add(stroke)
                                                val index = strokes.lastIndex

                                                do {
                                                    val event =
                                                        awaitPointerEvent()
                                                    val change =
                                                        event.changes
                                                            .firstOrNull {
                                                                it.id == down.id
                                                            } ?: break

                                                    stroke = stroke.copy(
                                                        points = stroke.points +
                                                                normalize(
                                                                    change.position
                                                                )
                                                    )
                                                    strokes[index] = stroke
                                                    change.consume()
                                                } while (change.pressed)
                                            }
                                        }
                                    },
                            ) {
                                baseImage?.let {
                                    drawImage(
                                        image = it,
                                        dstSize = IntSize(
                                            size.width.roundToInt(),
                                            size.height.roundToInt(),
                                        ),
                                    )
                                }

                                val width = size.minDimension * 0.012f

                                strokes.forEach { stroke ->
                                    val points = stroke.points.map {
                                        Offset(
                                            it.x * size.width,
                                            it.y * size.height,
                                        )
                                    }

                                    points.firstOrNull()?.let {
                                        drawCircle(
                                            stroke.color,
                                            width / 2,
                                            it,
                                        )
                                    }

                                    points.zipWithNext().forEach { (a, b) ->
                                        drawLine(
                                            color = stroke.color,
                                            start = a,
                                            end = b,
                                            strokeWidth = width,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                }
                            }

                            Box {
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
                                    ) {
                                        Text(
                                            selectedType?.label
                                                ?: "Choose a class",
                                            style = MaterialTheme.typography
                                                .headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            "Tap to change ▾",
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
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "Draw directly on the white art area",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        palette.forEach { (label, color) ->
                            Surface(
                                onClick = { brushColor = color },
                                enabled = !busy,
                                shape = CircleShape,
                                color = color,
                                modifier = Modifier
                                    .size(40.dp)
                                    .semantics {
                                        contentDescription = "$label brush"
                                    }
                                    .border(
                                        if (brushColor == color) 3.dp else 1.dp,
                                        if (brushColor == color) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Gray
                                        },
                                        CircleShape,
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
                            },
                        ) {
                            Text("Undo")
                        }

                        TextButton(
                            enabled = !busy &&
                                    (strokes.isNotEmpty() || basePng != null),
                            onClick = {
                                strokes.clear()
                                basePng = null
                            },
                        ) {
                            Text("Clear art")
                        }
                    }

                    errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Button(
                        enabled = name.isNotBlank() &&
                                selectedType != null && !busy,
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(),
                        onClick = {
                            val drawing = strokes.toList()
                            val background = basePng
                            busy = true
                            errorMessage = null

                            scope.launch {
                                try {
                                    previewPng = withContext(
                                        Dispatchers.Default
                                    ) {
                                        exportDrawing(drawing, background)
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    errorMessage =
                                        "Could not prepare the preview."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Text(if (busy) "Preparing…" else "Preview card")
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
                if (!busy) {
                    previewPng = null
                    errorMessage = null
                }
            },
            containerColor = cardColor,
            titleContentColor = cardTextColor,
            textContentColor = cardTextColor,
            title = {
                Text(
                    name.trim(),
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
                        selectedType?.label.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    errorMessage?.let {
                        Text(it, textAlign = TextAlign.Center)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = cardTextColor
                    ),
                    onClick = {
                        if (!busy) {
                            val type = checkNotNull(selectedType)
                            val cardName = name.trim()
                            busy = true
                            errorMessage = null

                            scope.launch {
                                try {
                                    onSave(
                                        cardName,
                                        type,
                                        png,
                                    )
                                    previewPng = null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    errorMessage = cardApiError(error)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        if (busy) "Saving…"
                        else if (draft.isEditing) "Save changes"
                        else "Save card"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        previewPng = null
                        errorMessage = null
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

private fun exportDrawing(
    strokes: List<EditorStroke>,
    basePng: ByteArray?,
): ByteArray {
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

        basePng?.let {
            val background = requireNotNull(
                BitmapFactory.decodeByteArray(it, 0, it.size)
            ) {
                "Saved artwork could not be read."
            }

            try {
                canvas.drawBitmap(
                    background,
                    null,
                    Rect(0, 0, side, side),
                    paint,
                )
            } finally {
                background.recycle()
            }
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

            stroke.points.zipWithNext().forEach { (a, b) ->
                canvas.drawLine(
                    a.x * side,
                    a.y * side,
                    b.x * side,
                    b.y * side,
                    paint,
                )
            }
        }

        return ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}