package app.necessityq.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.ImportedRecipe
import app.necessityq.domain.NutritionFood
import app.necessityq.domain.Quantity
import app.necessityq.domain.RecipeImport
import app.necessityq.domain.Recipes
import kotlinx.coroutines.launch

/**
 * A recipe before it is saved: a Tzameret dish ([food]) and/or a recipe read from a web page ([imported]).
 * Shows the ingredients, the method and the nutrition values; nothing is saved until "הוספה למתכונים שלי".
 * Tzameret has no methods, so for its dishes the sheet offers to look one up on the web and import it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipePreviewSheet(
    vm: AppViewModel,
    food: NutritionFood?,
    imported: ImportedRecipe?,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val index by vm.nutrition.collectAsStateWithLifecycle()
    var recipe by remember(food?.code, imported) { mutableStateOf(imported) }
    val tzameretDish = remember(index, food) { food?.let { f -> index?.dishFrom(f, "preview") } }
    val name = recipe?.name?.takeIf { food == null && it.isNotBlank() } ?: tzameretDish?.name ?: recipe?.name.orEmpty()
    val ingredients = recipe?.ingredients?.takeIf { it.isNotEmpty() } ?: tzameretDish?.ingredients.orEmpty()
    val servings = recipe?.servings ?: tzameretDish?.servings
    val steps = remember(recipe) { Recipes.steps(recipe?.instructions.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DishTile(name, (food?.code ?: name.hashCode()).toString(), size = 52)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    val source = listOfNotNull(
                        food?.let { "מאגר צמרת" },
                        recipe?.sourceUrl?.let { runCatching { Uri.parse(it).host?.removePrefix("www.") }.getOrNull() },
                    ).joinToString(" · ")
                    val sub = listOfNotNull(source.takeIf { it.isNotEmpty() }, servings?.let { "$it מנות" }).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- nutrition values (Tzameret, per 100 g) ----
            food?.let { NutritionGrid(it) }

            // ---- ingredients ----
            PreviewTitle("מרכיבים · ${ingredients.size}")
            if (ingredients.isEmpty()) {
                Text("לא נמצאו מרכיבים.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ingredients.forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (food != null && recipe?.ingredients.isNullOrEmpty()) {
                    Text(
                        "לפי ההרכב במאגר צמרת, בלי כמויות. אפשר לערוך אחרי השמירה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- method ----
            PreviewTitle(if (steps.isEmpty()) "אופן הכנה" else "אופן הכנה · ${steps.size} שלבים")
            if (steps.isEmpty()) {
                Text(
                    if (food != null) "במאגר צמרת יש רק את ההרכב של המאכל, בלי אופן הכנה. אפשר למצוא מתכון ברשת ולייבא אותו מהקישור:"
                    else "בדף הזה לא נמצא אופן הכנה.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MethodFinder(vm, name, onFound = { recipe = it })
            } else {
                steps.forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                if (food != null && recipe != null) {
                    TextButton(onClick = { recipe = null }) { Text("ביטול הייבוא (חזרה להרכב מצמרת)") }
                }
            }

            HorizontalDivider()
            Button(
                onClick = {
                    val id = when {
                        food != null -> vm.addDishFromNutrition(food, recipe)
                        else -> recipe?.let(vm::addImportedRecipe)
                    }
                    if (id != null) onSaved(id)
                },
                enabled = food != null || recipe != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("הוספה למתכונים שלי", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("סגירה") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PreviewTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

/** Tzameret's main values per 100 g as small tiles; missing values are left out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutritionGrid(food: NutritionFood) {
    val values = listOfNotNull(
        food.kcal?.let { "קלוריות" to "${Quantity.formatNumber(it)} קק\"ל" },
        food.protein?.let { "חלבון" to "${Quantity.formatNumber(it)} גרם" },
        food.carbs?.let { "פחמימות" to "${Quantity.formatNumber(it)} גרם" },
        food.fat?.let { "שומן" to "${Quantity.formatNumber(it)} גרם" },
        food.fiber?.let { "סיבים" to "${Quantity.formatNumber(it)} גרם" },
        food.sodiumMg?.let { "נתרן" to "${Quantity.formatNumber(it)} מ\"ג" },
    )
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PreviewTitle("ערכים תזונתיים ל-100 גרם")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { (label, value) ->
                Column(
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Finding a method: "חיפוש ברשת" opens a web search for the dish; the user copies the link of a recipe
 * page and pastes it here (a link already on the clipboard is filled in), and the recipe on that page is read.
 */
@Composable
fun MethodFinder(vm: AppViewModel, dishName: String, onFound: (ImportedRecipe) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (link.isEmpty()) clipboardLink(context)?.let { link = it } }

    fun doImport() {
        val url = RecipeImport.urlIn(link) ?: run { error = "צריך קישור שמתחיל ב-https://"; return }
        loading = true
        error = null
        scope.launch {
            runCatching { vm.fetchRecipe(url) }
                .onSuccess { r -> if (r.hasInstructions || r.ingredients.isNotEmpty()) onFound(r) else error = "בדף הזה לא נמצא מתכון." }
                .onFailure { error = it.message ?: "הייבוא נכשל" }
            loading = false
        }
    }

    val err = error
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dishName.isNotBlank()) {
            OutlinedButton(onClick = {
                val q = Uri.encode("$dishName מתכון")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q"))) }
            }) {
                Icon(Icons.Default.TravelExplore, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("חיפוש \"$dishName\" ברשת")
            }
        }
        OutlinedTextField(
            link, { link = it; error = null },
            label = { Text("קישור למתכון") },
            placeholder = { Text("https://…") },
            singleLine = true,
            isError = err != null,
            supportingText = if (err != null) { { Text(err) } } else null,
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { doImport() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { doImport() }, enabled = link.isNotBlank() && !loading) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (loading) "קורא את הדף…" else "ייבוא מהקישור")
        }
    }
}

/** A web address on the clipboard, if there is one. */
private fun clipboardLink(context: Context): String? = runCatching {
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
    clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let(RecipeImport::urlIn)
}.getOrNull()

/**
 * On a saved recipe without a method: find one on the web and import it. The method is added; the
 * recipe's own ingredients are replaced only when asked (a Tzameret dish's list has no amounts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMethodSheet(vm: AppViewModel, dish: app.necessityq.domain.Dish, onDismiss: () -> Unit) {
    var found by remember { mutableStateOf<ImportedRecipe?>(null) }
    var replaceIngredients by remember { mutableStateOf(dish.nutritionCode != null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("אופן הכנה ל${dish.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            val r = found
            if (r == null) {
                MethodFinder(vm, dish.name, onFound = { found = it })
            } else {
                val steps = Recipes.steps(r.instructions)
                Text("נמצא: ${r.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (steps.isEmpty()) Text("בדף הזה לא נמצא אופן הכנה.")
                steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
                if (r.ingredients.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(replaceIngredients, { replaceIngredients = it })
                        Text("גם המרכיבים מהמתכון (${r.ingredients.size}, עם כמויות)", Modifier.weight(1f))
                    }
                }
                Button(
                    enabled = steps.isNotEmpty() || (replaceIngredients && r.ingredients.isNotEmpty()),
                    onClick = {
                        vm.editDish(dish.id) { d ->
                            if (replaceIngredients && r.ingredients.isNotEmpty()) {
                                val servings = r.servings ?: d.servings
                                d.copy(instructions = r.instructions.ifBlank { d.instructions }, ingredients = r.ingredients, servings = servings, cookServings = servings)
                            } else {
                                d.copy(instructions = r.instructions.ifBlank { d.instructions })
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("שמירה במתכון") }
                TextButton(onClick = { found = null }) { Text("קישור אחר") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** "ייבוא מקישור" in recipe search: paste a recipe page's link, see the recipe, then decide whether to save it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkImportSheet(vm: AppViewModel, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var found by remember { mutableStateOf<ImportedRecipe?>(null) }
    found?.let { r ->
        RecipePreviewSheet(vm, food = null, imported = r, onDismiss = onDismiss, onSaved = onSaved)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("ייבוא מתכון מקישור", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                "מעתיקים את הקישור לדף של מתכון (מכל אתר מתכונים) ומדביקים כאן. השם, המרכיבים, אופן ההכנה ומספר המנות נקראים מהדף, ותראו אותם לפני השמירה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MethodFinder(vm, dishName = "", onFound = { found = it })
            Spacer(Modifier.height(16.dp))
        }
    }
}
