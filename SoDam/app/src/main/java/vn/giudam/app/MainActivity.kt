package vn.giudam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private lateinit var dao: GuestDao
    private var speechTarget by mutableStateOf<(String) -> Unit>({})
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var exportLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dao = GuestDb.get(this).dao()
        lifecycleScope.launch { if (dao.eventCount() == 0) dao.insertEvent(EventRecord(name="Sổ đi đám")) }
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) lifecycleScope.launch {
                val guests = JSONArray(); dao.snapshot().forEach { r -> guests.put(JSONObject().put("name",r.name).put("nickname",r.nickname).put("address",r.address).put("phone",r.phone).put("amount",r.amount).put("occasion",r.occasion).put("date",r.date).put("note",r.note).put("eventId",r.eventId)) }; val eventArray=JSONArray(); dao.eventSnapshot().forEach { e -> eventArray.put(JSONObject().put("id",e.id).put("name",e.name).put("date",e.date).put("note",e.note)) }
                contentResolver.openOutputStream(uri)?.use { it.write(JSONObject().put("version",2).put("events",eventArray).put("records",guests).toString(2).toByteArray()) }
            }
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) lifecycleScope.launch {
                val raw = contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).readText() } ?: return@launch
                val root=JSONObject(raw); val array=root.getJSONArray("records"); dao.clear(); dao.clearEvents(); val idMap=mutableMapOf<Long,Long>(); val savedEvents=root.optJSONArray("events")
                if(savedEvents!=null){for(i in 0 until savedEvents.length()){val e=savedEvents.getJSONObject(i);val oldId=e.optLong("id",1);val newId=dao.insertEvent(EventRecord(name=e.optString("name","Sổ đi đám"),date=e.optString("date"),note=e.optString("note")));idMap[oldId]=newId}}
                if(idMap.isEmpty()) idMap[1]=dao.insertEvent(EventRecord(name="Sổ đi đám"))
                for (i in 0 until array.length()) { val o=array.getJSONObject(i); val oldEvent=o.optLong("eventId",1); dao.insert(GuestRecord(name=o.optString("name"), nickname=o.optString("nickname"), address=o.optString("address"), phone=o.optString("phone"), amount=o.optLong("amount"), occasion=o.optString("occasion","Khác"), date=o.optString("date"), note=o.optString("note"), eventId=idMap[oldEvent] ?: idMap.values.first())) }
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        setContent { MainScreen(dao, ::startVoice, { exportLauncher.launch("Sao_luu_di_dam.json") }, { importLauncher.launch(arrayOf("application/json", "text/*")) }) }
    }
    private fun startVoice(onResult: (String) -> Unit) {
        speechTarget = onResult
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { onResult(""); return }
        speechRecognizer?.destroy(); speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(rmsdB: Float) {}; override fun onBufferReceived(buffer: ByteArray?) {}; override fun onEndOfSpeech() {}; override fun onError(error: Int) {}
            override fun onResults(results: Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { speechTarget(it) } }; override fun onPartialResults(partialResults: Bundle?) {}; override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE,"vi-VN"); putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PROMPT,"Xin mời nói"); putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1) }
        speechRecognizer?.startListening(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(dao: GuestDao, voice: ((String)->Unit)->Unit, export: ()->Unit, import: ()->Unit) {
    val scope= rememberCoroutineScope()
    var events by remember { mutableStateOf(emptyList<EventRecord>()) }; var currentEvent by remember { mutableStateOf<EventRecord?>(null) }
    var records by remember { mutableStateOf(emptyList<GuestRecord>()) }; var search by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }; var nickname by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var date by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var occasion by remember { mutableStateOf("Đám cưới") }; var editId by remember { mutableStateOf<Long?>(null) } ; var showForm by remember { mutableStateOf(false) }
    var showEventForm by remember { mutableStateOf(false) }; var eventName by remember { mutableStateOf("") }; var eventDate by remember { mutableStateOf("") }; var eventNote by remember { mutableStateOf("") }; var editEventId by remember { mutableStateOf<Long?>(null) }; var showEventMenu by remember { mutableStateOf(false) }; var showSettings by remember { mutableStateOf(false) }
    val categories=listOf("Đám cưới","Đám tang","Thôi nôi","Tân gia","Sinh nhật","Đám giỗ","Khác")
    LaunchedEffect(Unit) { dao.events().collectLatest { events=it; if(currentEvent==null && it.isNotEmpty()) currentEvent=it.first() else if(currentEvent!=null) currentEvent=it.find { e->e.id==currentEvent!!.id } ?: it.firstOrNull() } }
    LaunchedEffect(currentEvent?.id,search) { val id=currentEvent?.id ?: return@LaunchedEffect; (if(search.isBlank()) dao.all(id) else dao.search(id,search)).collectLatest { records=it } }
    val total=records.sumOf { it.amount }; val money: (Long)->String = { it.toString().replace(Regex("\\B(?=(\\d{3})+(?!\\d))"),".") }
    fun resetGuest(){name="";nickname="";address="";phone="";amount="";date="";note="";occasion="Đám cưới";editId=null}
    fun resetEvent(){eventName="";eventDate="";eventNote="";editEventId=null}
    MaterialTheme(colorScheme=lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF237A57),secondary=androidx.compose.ui.graphics.Color(0xFF548E70))) {
        Scaffold(topBar={TopAppBar(title={Text("Sổ Đám",fontWeight=FontWeight.Bold)},actions={TextButton(onClick={showSettings=true}){Text("Sao lưu")}})},floatingActionButton={FloatingActionButton(onClick={resetGuest();showForm=true}){Text("+",fontSize=28.sp)}}){ padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=14.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(14.dp)){Text("Tổng tiền đám đang chọn",fontSize=16.sp);Text("${money(total)} đ",fontSize=26.sp,fontWeight=FontWeight.Bold);Text("${records.size} người")}}
                ExposedDropdownMenuBox(expanded=showEventMenu,onExpandedChange={showEventMenu=!showEventMenu}) { OutlinedTextField(value=currentEvent?.name ?: "Chọn đám",onValueChange={},readOnly=true,label={Text("Sổ / Tên đám")},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded=showEventMenu,onDismissRequest={showEventMenu=false}){events.forEach{DropdownMenuItem(text={Text(it.name+if(it.date.isNotBlank()) " • ${it.date}" else "")},onClick={currentEvent=it;search="";showEventMenu=false})}} }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){Button(onClick={resetEvent();showEventForm=true},modifier=Modifier.weight(1f)){Text("+ Thêm đám")};OutlinedButton(onClick={currentEvent?.let{eventName=it.name;eventDate=it.date;eventNote=it.note;editEventId=it.id;showEventForm=true}},modifier=Modifier.weight(1f),enabled=currentEvent!=null){Text("Sửa tên đám")}}
                OutlinedTextField(search,{search=it},label={Text("Tìm người trong đám này")},modifier=Modifier.fillMaxWidth(),singleLine=true,trailingIcon={IconButton(onClick={voice{search=it}}){Text("🎙️")}})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){Button(onClick={resetGuest();showForm=true},modifier=Modifier.weight(1f)){Text("+ Thêm người")};OutlinedButton(onClick=export,modifier=Modifier.weight(1f)){Text("Xuất sao lưu")}}
                Text("Danh sách",fontSize=21.sp,fontWeight=FontWeight.Bold)
                if(records.isEmpty()) Text("Đám này chưa có dữ liệu. Hãy bấm Thêm người.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                records.forEach{r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(r.name,fontSize=19.sp,fontWeight=FontWeight.Bold);if(r.nickname.isNotBlank())Text("Biệt danh: ${r.nickname}");if(r.address.isNotBlank())Text("Địa chỉ: ${r.address}");Text("${r.occasion} • ${r.date}");Text("${money(r.amount)} đ",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=18.sp);if(r.note.isNotBlank())Text("Ghi chú: ${r.note}");Row{TextButton(onClick={name=r.name;nickname=r.nickname;address=r.address;phone=r.phone;amount=r.amount.toString();date=r.date;note=r.note;occasion=r.occasion;editId=r.id;showForm=true}){Text("Sửa")};TextButton(onClick={scope.launch{dao.delete(r)}}){Text("Xóa",color=MaterialTheme.colorScheme.error)}}}}
                Spacer(Modifier.height(75.dp))
            }
        }
        if(showEventForm) AlertDialog(onDismissRequest={showEventForm=false},title={Text(if(editEventId==null)"Thêm đám mới" else "Sửa tên đám")},text={Column(verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(eventName,{eventName=it},label={Text("Tên đám (VD: Đám cưới Lan, Đám tang bác Năm)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(eventDate,{eventDate=it},label={Text("Ngày tổ chức")},modifier=Modifier.fillMaxWidth());OutlinedTextField(eventNote,{eventNote=it},label={Text("Ghi chú")},modifier=Modifier.fillMaxWidth());if(editEventId!=null)TextButton(onClick={val e=events.find{it.id==editEventId};if(e!=null)scope.launch{dao.deleteGuestsForEvent(e.id);dao.deleteEvent(e);if(events.size<=1)dao.insertEvent(EventRecord(name="Sổ đi đám"));currentEvent=null};showEventForm=false}){Text("Xóa đám này",color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=eventName.isNotBlank(),onClick={scope.launch{if(editEventId==null){val id=dao.insertEvent(EventRecord(name=eventName.trim(),date=eventDate,note=eventNote));currentEvent=EventRecord(id=id,name=eventName.trim(),date=eventDate,note=eventNote)}else{val e=EventRecord(id=editEventId!!,name=eventName.trim(),date=eventDate,note=eventNote);dao.updateEvent(e);currentEvent=e};showEventForm=false}}){Text("Lưu")}},dismissButton={TextButton(onClick={showEventForm=false}){Text("Hủy")}})
        if(showForm) AlertDialog(onDismissRequest={showForm=false},title={Text(if(editId==null)"Thêm người đi đám" else "Sửa thông tin")},text={Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){VoiceInput("Tên người",name,{name=it},voice);VoiceInput("Biệt danh",nickname,{nickname=it},voice);VoiceInput("Địa chỉ",address,{address=it},voice);VoiceInput("Số điện thoại",phone,{phone=it},voice);OutlinedTextField(amount,{amount=it.filter{c->c.isDigit()}},label={Text("Số tiền (VNĐ)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(date,{date=it},label={Text("Ngày đi đám")},modifier=Modifier.fillMaxWidth());var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=!expanded}){OutlinedTextField(occasion,{},readOnly=true,label={Text("Loại sự kiện")},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded,{expanded=false}){categories.forEach{DropdownMenuItem(text={Text(it)},onClick={occasion=it;expanded=false})}}};VoiceInput("Ghi chú",note,{note=it},voice)}},confirmButton={Button(enabled=name.isNotBlank()&&currentEvent!=null,onClick={val item=GuestRecord(id=editId?:0,name=name.trim(),nickname=nickname.trim(),address=address.trim(),phone=phone.trim(),amount=amount.toLongOrNull()?:0L,occasion=occasion,date=date,note=note,eventId=currentEvent!!.id);scope.launch{if(editId==null)dao.insert(item)else dao.update(item);resetGuest();showForm=false}}){Text("Lưu")}},dismissButton={TextButton(onClick={showForm=false}){Text("Hủy")}})
        if(showSettings) AlertDialog(onDismissRequest={showSettings=false},title={Text("Sao lưu dữ liệu")},text={Text("Dữ liệu lưu offline. Xuất tệp định kỳ để tránh mất dữ liệu khi đổi máy.")},confirmButton={TextButton(onClick={export();showSettings=false}){Text("Xuất tệp")}},dismissButton={TextButton(onClick={import();showSettings=false}){Text("Khôi phục")}})
    }
}
@Composable fun VoiceInput(label:String,value:String,onChange:(String)->Unit,voice:((String)->Unit)->Unit){OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={voice(onChange)}){Text("🎙️")}})}

@Composable fun VoiceInput(l:String,v:String,c:(String)->Unit,vo:((String)->Unit)->Unit){OutlinedTextField(v,c,label={Text(l)},modifier=Modifier.fillMaxWidth(),singleLine=true,trailingIcon={IconButton(onClick={vo(c)}){Text("🎙️")}})}

@Composable fun VoiceInput(l:String,v:String,c:(String)->Unit,vo:((String)->Unit)->Unit){OutlinedTextField(v,c,label={Text(l)},modifier=Modifier.fillMaxWidth(),singleLine=true,trailingIcon={IconButton(onClick={vo(c)}){Text("🎙️")}})}

@Composable fun VoiceInput(l:String,v:String,c:(String)->Unit,vo:((String)->Unit)->Unit){OutlinedTextField(v,c,label={Text(l)},modifier=Modifier.fillMaxWidth(),singleLine=true,trailingIcon={IconButton(onClick={vo(c)}){Text("🎙️")}})}

@Composable fun VoiceInput(l:String,v:String,c:(String)->Unit,vo:((String)->Unit)->Unit){OutlinedTextField(v,c,label={Text(l)},modifier=Modifier.fillMaxWidth(),singleLine=true,trailingIcon={IconButton(onClick={vo(c)}){Text("🎙️")}})}

@Composable fun VoiceInput(l:String,v:String,c:(String)->Unit,vo:((String)->Unit)->Unit){OutlinedTextField(v,c,label={Text(l)},modifier=Modifier.fillMaxWidth(),singleLine=true,trailingIcon={IconButton(onClick={vo(c)}){Text("🎙️")}})}
