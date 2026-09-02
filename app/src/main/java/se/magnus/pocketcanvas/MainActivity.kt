package se.magnus.pocketcanvas

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class UiElement(val type: String, val text: String, val target: String? = null)
data class Frame(val id: String, val title: String, val x: Float, val y: Float, val elements: List<UiElement>)
data class Wire(val from: String, val to: String)
data class Project(val name: String, val frames: List<Frame>, val wires: List<Wire>)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var project by mutableStateOf(sampleProject()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    fun moveFrame(id: String, dx: Float, dy: Float) {
        project = project.copy(frames = project.frames.map {
            if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
        })
    }

    suspend fun generate(prompt: String, apiKey: String) {
        busy = true; error = null
        try { project = OpenAiDesigner.generate(prompt, apiKey) }
        catch (t: Throwable) { error = t.message ?: "Okänt fel" }
        finally { busy = false }
    }

    companion object {
        fun sampleProject() = Project("Min app", listOf(
            Frame("start", "Välkommen", 60f, 90f, listOf(UiElement("heading", "Min nya app"), UiElement("text", "Beskriv en idé för AI:n."), UiElement("button", "Kom igång", "home"))),
            Frame("home", "Hem", 430f, 250f, listOf(UiElement("heading", "Översikt"), UiElement("card", "Ditt innehåll"), UiElement("button", "Öppna", null)))
        ), listOf(Wire("start", "home")))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9A86FF))) { PocketCanvasApp() } }
    }
}

@Composable fun PocketCanvasApp(vm: MainViewModel = viewModel()) {
    var prompt by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyStore = remember { KeyStore(context.applicationContext as Application) }
    var key by remember { mutableStateOf(keyStore.get()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { Surface(color = Color(0xFF17151F)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pocket Canvas", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f));
            TextButton(onClick = { showSettings = true }) { Text("API-nyckel") }
        } } },
        bottomBar = { Surface(color = Color(0xFF17151F), tonalElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(prompt, { prompt = it }, placeholder = { Text("Beskriv appen du vill skapa…") }, modifier = Modifier.weight(1f), maxLines = 3)
                Spacer(Modifier.width(8.dp))
                Button(enabled = !vm.busy && prompt.isNotBlank() && key.isNotBlank(), onClick = { scope.launch { vm.generate(prompt, key) } }) { Text(if (vm.busy) "…" else "Skapa") }
            }
        } }
    ) { pad -> Box(Modifier.fillMaxSize().padding(pad)) {
        InfiniteBoard(vm.project, vm::moveFrame)
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.TopCenter).background(Color(0xEE24151A)).padding(12.dp)) }
    } }

    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("OpenAI API-nyckel") }, text = {
        Column { Text("Privat testläge. Publicera aldrig en app med nyckeln i klienten."); Spacer(Modifier.height(8.dp)); OutlinedTextField(key, { key = it }, label = { Text("sk-…") }) }
    }, confirmButton = { Button(onClick = { keyStore.save(key.trim()); showSettings = false }) { Text("Spara krypterat") } }, dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Avbryt") } })
}

@Composable fun InfiniteBoard(project: Project, move: (String, Float, Float) -> Unit) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(.72f) }
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize().background(Color(0xFF0E0D14)).pointerInput(Unit) {
        detectTransformGestures { _, change, z, _ -> pan += change; zoom = (zoom * z).coerceIn(.35f, 1.6f) }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val dot = Color(0xFF2A2734)
            val step = 42f * zoom
            var x = pan.x % step
            while (x < size.width) { var y = pan.y % step; while (y < size.height) { drawCircle(dot, 1.5f, Offset(x,y)); y += step }; x += step }
            project.wires.forEach { wire ->
                val a = project.frames.find { it.id == wire.from }; val b = project.frames.find { it.id == wire.to }
                if (a != null && b != null) {
                    val start = Offset(pan.x + (a.x + 280f) * density * zoom, pan.y + (a.y + 250f) * density * zoom)
                    val end = Offset(pan.x + b.x * density * zoom, pan.y + (b.y + 250f) * density * zoom)
                    val path = Path().apply { moveTo(start.x,start.y); cubicTo(start.x+90,end.y-20,end.x-90,end.y+20,end.x,end.y) }
                    drawPath(path, Color(0xFF806BFF), style = Stroke(4f))
                }
            }
        }
        project.frames.forEach { frame ->
            DeviceFrame(frame, zoom, pan, density, onMove = { dx,dy -> move(frame.id, dx/(zoom*density), dy/(zoom*density)) })
        }
    }
}

@Composable fun DeviceFrame(frame: Frame, zoom: Float, pan: Offset, density: Float, onMove: (Float,Float)->Unit) {
    val width = (280 * zoom).dp; val height = (500 * zoom).dp
    Column(Modifier.offset { IntOffset((pan.x + frame.x*density*zoom).roundToInt(), (pan.y + frame.y*density*zoom).roundToInt()) }
        .width(width).height(height).background(Color(0xFFFBF9FF), RoundedCornerShape((28*zoom).dp)).border(2.dp, Color(0xFF5A536A), RoundedCornerShape((28*zoom).dp))) {
        Box(Modifier.fillMaxWidth().background(Color(0xFF211E2B)).padding((12*zoom).dp).pointerInput(frame.id) { detectDragGestures { c,d -> c.consume(); onMove(d.x,d.y) } }) {
            Text(frame.title, color=Color.White, fontWeight=FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxSize().padding((16*zoom).dp), verticalArrangement = Arrangement.spacedBy((10*zoom).dp)) {
            items(frame.elements) { element -> ElementPreview(element) }
        }
    }
}

@Composable fun ElementPreview(e: UiElement) = when(e.type) {
    "heading" -> Text(e.text, color=Color(0xFF17131F), style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
    "button" -> Button(onClick={}, modifier=Modifier.fillMaxWidth()) { Text(e.text) }
    "card" -> Surface(color=Color(0xFFEDE8FF), shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) { Text(e.text, color=Color(0xFF201B31), modifier=Modifier.padding(16.dp)) }
    else -> Text(e.text, color=Color(0xFF4D465A))
}

object OpenAiDesigner {
    suspend fun generate(prompt: String, key: String): Project = withContext(Dispatchers.IO) {
        val schema = JSONObject("""{"type":"object","additionalProperties":false,"required":["name","frames","wires"],"properties":{"name":{"type":"string"},"frames":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["id","title","x","y","elements"],"properties":{"id":{"type":"string"},"title":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"elements":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["type","text","target"],"properties":{"type":{"type":"string","enum":["heading","text","button","card"]},"text":{"type":"string"},"target":{"type":["string","null"]}}}}}}},"wires":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["from","to"],"properties":{"from":{"type":"string"},"to":{"type":"string"}}}}}}""")
        val body = JSONObject().put("model","gpt-5-mini").put("input", JSONArray()
            .put(JSONObject().put("role","system").put("content","Du är en mobil UX-designer. Skapa 3–7 tydliga Android-skärmar. Placera dem med minst 350 enheter mellan x-positionerna. Alla button-target måste vara ett frame-id eller null."))
            .put(JSONObject().put("role","user").put("content",prompt)))
            .put("text", JSONObject().put("format", JSONObject().put("type","json_schema").put("name","app_flow").put("strict",true).put("schema",schema)))
        val conn = URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection
        conn.requestMethod="POST"; conn.doOutput=true; conn.setRequestProperty("Authorization","Bearer $key"); conn.setRequestProperty("Content-Type","application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code=conn.responseCode; val raw=(if(code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if(code !in 200..299) throw IllegalStateException(JSONObject(raw).optJSONObject("error")?.optString("message") ?: "API-fel $code")
        val response = JSONObject(raw)
        val output = response.getJSONArray("output")
        var outputText: String? = null
        for (i in 0 until output.length()) {
            val content = output.getJSONObject(i).optJSONArray("content") ?: continue
            for (k in 0 until content.length()) {
                val part = content.getJSONObject(k)
                if (part.optString("type") == "output_text") outputText = part.optString("text")
            }
        }
        parse(outputText ?: throw IllegalStateException("AI-svaret saknade ett appflöde"))
    }
    private fun parse(raw:String):Project { val j=JSONObject(raw); val fs=j.getJSONArray("frames"); val ws=j.getJSONArray("wires")
        return Project(j.getString("name"),(0 until fs.length()).map { i-> val f=fs.getJSONObject(i); val es=f.getJSONArray("elements"); Frame(f.getString("id"),f.getString("title"),f.getDouble("x").toFloat(),f.getDouble("y").toFloat(),(0 until es.length()).map { k->val e=es.getJSONObject(k); UiElement(e.getString("type"),e.getString("text"),if(e.isNull("target")) null else e.getString("target"))}) },(0 until ws.length()).map { i->val w=ws.getJSONObject(i);Wire(w.getString("from"),w.getString("to"))}) }
}

class KeyStore(app: Application) {
    private val prefs = androidx.security.crypto.EncryptedSharedPreferences.create("secrets", androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC), app, androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun get()=prefs.getString("openai","") ?: ""
    fun save(value:String)=prefs.edit().putString("openai",value).apply()
}
