package com.hemanager.mobile.feature.tools

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hemanager.mobile.ui.theme.HeColors
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

@Composable internal fun NativeExternalScreen(server: String,token: String,onBack: ()->Unit,onLogout: ()->Unit) {
    var source by rememberSaveable {mutableStateOf("")}
    when(source) {
        "x" -> NativeXScreen(server,token,{source=""},onLogout)
        "wnacg","asmr" -> key(source) { NativeSourceScreen(server,token,source,{source=""},onLogout) }
        else -> ToolPage("外部收藏与下载",onBack) {p -> Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("选择数据源",style=MaterialTheme.typography.headlineSmall)
            ToolCard("WNACG","漫画收藏同步、批量下载和自动同步",androidx.compose.material.icons.Icons.Default.CloudDownload) {source="wnacg"}
            ToolCard("X","归档导入、喜欢同步和原生任务管理",androidx.compose.material.icons.Icons.Default.AlternateEmail) {source="x"}
            ToolCard("ASMR","作品同步、格式筛选和批量下载",androidx.compose.material.icons.Icons.Default.Headphones) {source="asmr"}
        } }
    }
}

@Composable private fun NativeSourceScreen(server: String,token: String,type: String,onBack: ()->Unit,onLogout: ()->Unit) {
    val state=rememberNativeActions(server,token,onLogout);val context=LocalContext.current
    val store=remember(server) {NativeJobStore(context,server)}
    var sources by remember {mutableStateOf<List<JSONObject>>(emptyList())};var source by remember {mutableStateOf<JSONObject?>(null)}
    var records by remember {mutableStateOf<List<JSONObject>>(emptyList())};var total by remember {mutableIntStateOf(0)}
    var page by rememberSaveable {mutableIntStateOf(0)};var search by rememberSaveable {mutableStateOf("")};var applied by rememberSaveable {mutableStateOf("")}
    var url by rememberSaveable {mutableStateOf(if(type=="wnacg") "https://www.wnacg.com/users-users_fav.html" else "https://api.asmr-200.com")}
    var root by rememberSaveable {mutableStateOf("")};var cookie by remember {mutableStateOf("")}
    var username by rememberSaveable {mutableStateOf("")};var password by remember {mutableStateOf("")}
    var mirrors by rememberSaveable {mutableStateOf("")};var playlist by rememberSaveable {mutableStateOf("")}
    var format by rememberSaveable {mutableStateOf("all")};var version by rememberSaveable {mutableStateOf("all")}
    var pageLimit by rememberSaveable {mutableStateOf(if(type=="wnacg") "30" else "3")}
    var selected by remember {mutableStateOf<Set<Int>>(emptySet())};var job by remember {mutableStateOf<JSONObject?>(null)}
    var detail by rememberSaveable {mutableIntStateOf(0)};var exportText by remember {mutableStateOf("")}
    var configuration by rememberSaveable {mutableStateOf(false)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri -> if(uri!=null) state.run {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {it.write(exportText.toByteArray())} ?: error("无法保存文件")};state.message="链接已导出"
    } }
    fun useSource(s: JSONObject) {
        source=s;url=s.text("favorites_url",url);root=s.text("download_root_path");selected=emptySet();page=0
        if(type=="asmr") {
            val saved=runCatching {JSONObject(store.get("settings:asmr:${s.optInt("id")}"))}.getOrNull()
            mirrors=s.text("api_mirrors",saved?.text("api_mirrors").orEmpty());playlist=s.text("playlist_url",saved?.text("playlist_url").orEmpty())
            format=s.text("audio_format_filter",saved?.text("audio_format_filter","all") ?: "all");version=s.text("audio_version_filter",saved?.text("audio_version_filter","all") ?: "all")
        }
    }
    fun saveAsmrSettings() {if(type=="asmr"&&source!=null) store.set("settings:asmr:${source!!.optInt("id")}",obj("api_mirrors" to mirrors,"playlist_url" to playlist,"audio_format_filter" to format,"audio_version_filter" to version).toString())}
    suspend fun loadItems() {
        val response=state.api.request(query("/external/favorites","source_type" to type,"source_id" to source?.optInt("id"),"search" to applied,"limit" to 24,"offset" to page*24))
        records=(response.value as JSONArray).objects();total=response.total ?: records.size
        selected=selected.intersect(records.filter {it.optInt("local_media_id")==0}.map {it.optInt("id")}.toSet())
    }
    suspend fun loadSources() {
        sources=state.api.list("/external/sources").filter {it.text("source_type")==type}
        val s=sources.find {it.optInt("id")==source?.optInt("id")} ?: sources.firstOrNull()
        if(s!=null) useSource(s)
    }
    suspend fun refresh() {loadSources();loadItems()}
    LaunchedEffect(Unit) {state.run {
        refresh();val id=store.get("external:$type");if(id.isNotBlank()) runCatching {job=state.api.get("/external/downloads/${encoded(id)}")}
    }}
    LaunchedEffect(page,applied,source?.optInt("id")) {if(source!=null) state.run {loadItems()}}
    NativePoll(job?.text("job_id"),activeJob(job)) {
        try {val was=activeJob(job);job=state.api.get("/external/downloads/${encoded(job!!.text("job_id"))}");if(was&&!activeJob(job)) loadItems()}
        catch(e: CancellationException) {throw e} catch(e: Exception) {state.error=e.message ?: "任务更新失败"}
    }
    if(detail>0) {NativeMediaDetail(server,token,detail,{detail=0},onLogout);return}
    NativeScene(if(type=="wnacg") "WNACG 收藏" else "ASMR 收藏",state,onBack,{state.run {refresh()}}) {
        item { NativeSection("数据源") {
            if(sources.isEmpty()) Text("首次同步后会创建数据源")
            sources.forEach {s->OutlinedButton(onClick={useSource(s)},enabled=!state.busy) {Text("${if(s.optInt("id")==source?.optInt("id")) "✓ " else ""}${s.text("name")} · ${statusName(s.text("status"))}")} }
            source?.let {Text("上次同步：${it.text("last_synced_at","尚未同步")}");if(it.text("last_error").isNotBlank()) Text(it.text("last_error"),color=MaterialTheme.colorScheme.error)}
            TextButton(onClick={configuration=!configuration}) {Text(if(configuration) "收起同步设置" else "同步设置")}
            if(configuration) {
                NativeField(if(type=="wnacg") "收藏页地址" else "API 地址",url,{url=it},enabled=!state.busy)
                if(type=="wnacg") NativeField("Cookie（留空沿用已保存值）",cookie,{cookie=it},secret=true,enabled=!state.busy)
                else {
                    NativeField("用户名（首次登录）",username,{username=it},enabled=!state.busy)
                    NativeField("密码（留空沿用会话）",password,{password=it},secret=true,enabled=!state.busy)
                    NativeField("镜像地址，每行一个",mirrors,{mirrors=it},enabled=!state.busy,multiline=true)
                OutlinedButton(onClick={state.run {val results=state.api.send("/external/asmr/mirrors/ping",obj("api_base" to url,"api_mirrors" to mirrors)).optJSONArray("results").objects();state.message=results.joinToString("\n") {"${it.text("base")} · ${if(it.optBoolean("ok")) "${it.optInt("latency_ms")} ms" else it.text("error","无法连接")}"}}},enabled=!state.busy) {Text("检测镜像连通性")}
                    NativeField("播放列表网址（可选）",playlist,{playlist=it},enabled=!state.busy)
                    NativeChoice("音频格式",format,listOf("all" to "全部","no_wav" to "排除 WAV","mp3_only" to "仅 MP3"),!state.busy) {format=it}
                    NativeChoice("音效版本",version,listOf("all" to "全部","no_se" to "无音效","se_only" to "仅音效版"),!state.busy) {version=it}
                }
                NativeField("同步页数（1–30）",pageLimit,{pageLimit=it},enabled=!state.busy)
                Button(onClick={state.run {
                    val n=pageLimit.toIntOrNull();require(n!=null&&n in 1..30) {"同步页数必须为 1–30"}
                    val body=if(type=="wnacg") obj("name" to "WNACG","favorites_url" to url.trim(),"cookie" to cookie.trim().ifBlank {null},"page_limit" to n)
                        else obj("name" to "ASMR","api_base" to url.trim(),"api_mirrors" to mirrors,"playlist_url" to playlist,"username" to username.ifBlank {null},"password" to password.ifBlank {null},"page_limit" to n,"audio_format_filter" to format,"audio_version_filter" to version)
                    source?.let {body.put("source_id",it.optInt("id"))}
                    val result=state.api.send("/external/$type/sync",body);source=result.optJSONObject("source");saveAsmrSettings();cookie="";password="";refresh();state.message="同步完成：${result.optInt("synced_count")} 个作品"
                }},enabled=!state.busy) {Text("同步收藏")}
            }
        } }
        item { NativeSection("下载设置") {
            NativeField("服务器下载目录",root,{root=it},enabled=!state.busy)
            OutlinedButton(onClick={state.run {
                require(root.isNotBlank()) {"请填写下载目录"};require(source!=null) {"请先同步创建数据源"}
                val body=obj("download_root_path" to root.trim())
                if(type=="asmr") {body.put("audio_format_filter",format);body.put("audio_version_filter",version);body.put("playlist_url",playlist);body.put("api_mirrors",mirrors)}
                source=state.api.send("/external/sources/${source!!.optInt("id")}",body,"PATCH");saveAsmrSettings();state.message="下载设置已保存"
            }},enabled=!state.busy&&source!=null) {Text("保存下载设置")}
        } }
        if(type=="wnacg"&&source!=null) item { NativeAutoSync(state,"wnacg",source!!) {source=it} }
        item { JobStatus(job) }
        job?.let {j->item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(activeJob(j)) OutlinedButton(onClick={state.confirm("取消当前下载任务？") {job=state.api.send("/external/downloads/${encoded(j.text("job_id"))}/cancel")}},enabled=!state.busy) {Text("取消任务")}
            if(!activeJob(j)&&j.optInt("failed")>0) Button(onClick={state.run {
                val ids=j.optJSONArray("tasks").objects().filter {it.text("status") in setOf("failed","error")}.map {it.optInt("item_id")}.filter {it>0}
                require(ids.isNotEmpty()) {"没有可重试的失败任务"};job=state.api.send("/external/$type/downloads",obj("item_ids" to JSONArray(ids),"download_root_path" to root));store.set("external:$type",job!!.text("job_id"))
            }},enabled=!state.busy) {Text("重试失败项")}
        } } }
        item { NativeSection("作品 · 共 $total 个") {
            NativeField("搜索标题或分类",search,{search=it},enabled=!state.busy)
            Button(onClick={page=0;applied=search.trim();state.run {loadItems()}},enabled=!state.busy) {Text("搜索")}
            Text("已选择 ${selected.size} 个未入库作品")
            OutlinedButton(onClick={selected=records.filter {it.optInt("local_media_id")==0}.map {it.optInt("id")}.toSet()},enabled=!state.busy) {Text("选择本页未入库作品")}
            if(selected.isNotEmpty()) {
                Button(onClick={state.confirm("下载 ${selected.size} 个作品到服务器目录“$root”？") {
                    require(root.isNotBlank()) {"请填写下载目录"}
                    job=state.api.send("/external/$type/downloads",obj("item_ids" to JSONArray(selected.toList()),"download_root_path" to root.trim()));store.set("external:$type",job!!.text("job_id"));selected=emptySet()
                }},enabled=!state.busy&&!activeJob(job)) {Text("下载选中作品")}
                TextButton(onClick={exportText=records.filter {it.optInt("id") in selected}.joinToString("\n\n") {"${it.text("title")}\n${it.text("url")}"};exporter.launch("he-manager-links.txt")},enabled=!state.busy) {Text("导出链接")}
                TextButton(onClick={selected=emptySet()}) {Text("清空选择")}
            }
        } }
        items(records,key={it.optInt("id")}) {item->NativeSection(item.text("title")) {
            AsyncImage(model=ImageRequest.Builder(context).data("$server/external/favorites/${item.optInt("id")}/cover").addHeader("Authorization","Bearer $token").build(),contentDescription=null,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(160.dp))
            Text(item.text("category_name"),color=HeColors.OpWhiteSoft)
            if(item.optInt("local_media_id")>0) Button(onClick={detail=item.optInt("local_media_id")},enabled=!state.busy) {Text("查看已入库作品")}
            else Row {Checkbox(item.optInt("id") in selected,{yes->selected=if(yes) selected+item.optInt("id") else selected-item.optInt("id")},enabled=!state.busy);Text("加入下载选择",Modifier.padding(top=12.dp))}
        } }
        item {NativePagination(page,total,24,!state.busy) {page=it}}
    }
}

@Composable internal fun NativeAutoSync(state: NativeActions,type: String,source: JSONObject,onUpdated: (JSONObject)->Unit) {
    var interval by rememberSaveable(source.optInt("id")) {mutableStateOf(source.optInt("auto_sync_interval_hours",24).toString())}
    var logs by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    NativeSection("自动同步") {
        Text("${if(source.optBoolean("auto_sync_enabled")) "已开启" else "已关闭"} · 下次运行 ${source.text("auto_sync_next_run_at","未计划")}")
        NativeField("间隔（1–168 小时）",interval,{interval=it},enabled=!state.busy)
        Button(onClick={state.run {
            val n=interval.toIntOrNull();require(n!=null&&n in 1..168) {"间隔必须为 1–168 小时"}
            onUpdated(state.api.send("/auto-sync/$type/${source.optInt("id")}",obj("auto_sync_enabled" to !source.optBoolean("auto_sync_enabled"),"auto_sync_interval_hours" to n),"PATCH"))
        }},enabled=!state.busy) {Text(if(source.optBoolean("auto_sync_enabled")) "关闭自动同步" else "开启自动同步")}
        OutlinedButton(onClick={state.run {
            val n=interval.toIntOrNull();require(n!=null&&n in 1..168) {"间隔必须为 1–168 小时"}
            onUpdated(state.api.send("/auto-sync/$type/${source.optInt("id")}",obj("auto_sync_interval_hours" to n),"PATCH"));state.message="自动同步间隔已保存"
        }},enabled=!state.busy) {Text("保存同步间隔")}
        OutlinedButton(onClick={state.confirm("立即执行一次同步？") {state.api.send("/auto-sync/$type/${source.optInt("id")}/trigger");state.message="已提交同步任务"}},enabled=!state.busy) {Text("立即运行")}
        TextButton(onClick={state.run {logs=state.api.list(query("/auto-sync/logs","source_type" to type,"source_id" to source.optInt("id"),"limit" to 10))}},enabled=!state.busy) {Text("查看运行记录")}
        logs.forEach {Text("${it.text("started_at")} · ${statusName(it.text("status"))}\n${it.text("message")}")}
    }
}

@Composable private fun NativeXScreen(server: String,token: String,onBack: ()->Unit,onLogout: ()->Unit) {
    val state=rememberNativeActions(server,token,onLogout);val context=LocalContext.current
    var source by remember {mutableStateOf<JSONObject?>(null)};var stats by remember {mutableStateOf<JSONObject?>(null)}
    var root by rememberSaveable {mutableStateOf("")};var cookie by remember {mutableStateOf("")}
    var job by remember {mutableStateOf<JSONObject?>(null)};var sync by remember {mutableStateOf<JSONObject?>(null)}
    var posts by remember {mutableStateOf<List<JSONObject>>(emptyList())};var page by rememberSaveable {mutableIntStateOf(0)}
    suspend fun refresh() {
        source=state.api.list("/x/sources").firstOrNull();val id=source?.optInt("id") ?: return
        root=source!!.text("download_root_path");stats=state.api.get("/x/sources/$id/stats")
        job=state.api.request("/x/sources/$id/active-job").value as? JSONObject
        sync=state.api.request("/x/sources/$id/active-sync").value as? JSONObject
        posts=state.api.list(query("/x/sources/$id/posts","limit" to 24,"offset" to page*24))
    }
    LaunchedEffect(page) {state.run {refresh()}}
    NativePoll(source?.optInt("id"),activeJob(job)||activeJob(sync)) {
        try {
            if(activeJob(job)) job=state.api.get("/x/imports/${encoded(job!!.text("job_id"))}")
            if(activeJob(sync)) sync=state.api.get("/x/syncs/${encoded(sync!!.text("job_id"))}")
            source?.let {stats=state.api.get("/x/sources/${it.optInt("id")}/stats")}
        } catch(e: CancellationException) {throw e} catch(e: Exception) {state.error=e.message ?: "任务更新失败"}
    }
    val upload=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null&&source!=null) state.run {
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null} ?: "archive.zip"
        val result=state.api.upload("/x/sources/${source!!.optInt("id")}/archive",name) {context.contentResolver.openInputStream(uri) ?: error("无法读取归档文件")}
        refresh();state.message="导入归档：新增 ${result.optInt("new_posts")}，已存在 ${result.optInt("existing_posts")}"
    } }
    NativeScene("X 归档与同步",state,onBack,{state.run {refresh()}}) {
        item { NativeSection("来源与下载目录") {
            Text(source?.text("name") ?: "正在读取来源")
            NativeField("服务器下载目录",root,{root=it},enabled=!state.busy)
            NativeField("Cookie（留空沿用已保存值）",cookie,{cookie=it},secret=true,enabled=!state.busy)
            Text(if(source?.optBoolean("cookie_saved")==true) "服务器已保存 Cookie" else "尚未保存 Cookie",color=HeColors.OpWhiteSoft)
            Button(onClick={state.run {
                require(source!=null&&root.isNotBlank()) {"请填写下载目录"}
                source=state.api.send("/x/sources/${source!!.optInt("id")}",obj("download_root_path" to root.trim(),"cookie" to cookie.trim().ifBlank {null}),"PATCH");cookie="";state.message="设置已保存"
            }},enabled=!state.busy&&source!=null) {Text("保存设置")}
            Button(onClick={upload.launch(arrayOf("*/*"))},enabled=!state.busy&&source!=null&&!activeJob(job)) {Text("选择并导入归档文件")}
            OutlinedButton(onClick={state.confirm("开始同步 X 喜欢列表？") {sync=state.api.send("/x/sources/${source!!.optInt("id")}/sync")}},enabled=!state.busy&&source!=null&&!activeJob(sync)) {Text("同步喜欢列表")}
        } }
        stats?.let {s->item {NativeSection("导入概况") {
            Text("帖子 ${s.optInt("total_posts")} · 已完成 ${s.optInt("completed_posts")} · 失败 ${s.optInt("failed_posts")} · 跳过 ${s.optInt("skipped_posts")}")
            Text("媒体 ${s.optInt("downloaded_media")} / ${s.optInt("total_media")}")
            listOf("" to "开始下载","failed" to "重试失败","skipped" to "重试跳过").forEach { (mode,label) -> Button(onClick={state.confirm("$label？文件将保存到服务器。") {job=state.api.send("/x/imports",obj("source_id" to source!!.optInt("id"),"retry_failed_only" to (mode=="failed"),"retry_skipped_only" to (mode=="skipped")))}},enabled=!state.busy&&source!=null&&!activeJob(job)) {Text(label)} }
        } } }
        item {JobStatus(job)}
        job?.let {j->item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(activeJob(j)||j.text("status")=="paused") {
                OutlinedButton(onClick={state.run {job=state.api.send("/x/imports/${encoded(j.text("job_id"))}/${if(j.text("status")=="paused") "resume" else "pause"}")}},enabled=!state.busy) {Text(if(j.text("status")=="paused") "继续" else "暂停")}
                OutlinedButton(onClick={state.confirm("取消当前导入任务？") {job=state.api.send("/x/imports/${encoded(j.text("job_id"))}/cancel")}},enabled=!state.busy) {Text("取消")}
            }
        } } }
        item {JobStatus(sync)}
        if(activeJob(sync)) item {OutlinedButton(onClick={state.confirm("取消喜欢同步？") {sync=state.api.send("/x/syncs/${encoded(sync!!.text("job_id"))}/cancel")}},enabled=!state.busy) {Text("取消同步")}}
        source?.let {s->item {NativeAutoSync(state,"x",s) {source=it}}}
        items(posts) {p->NativeSection(p.text("full_text",p.text("tweet_id","帖子"))) {
            Text("${p.text("author_name",p.text("author_screen_name"))} · ${statusName(p.text("status"))}")
            if(p.text("error_message").isNotBlank()) Text(p.text("error_message"),color=MaterialTheme.colorScheme.error)
        } }
        item {NativePagination(page,stats?.optInt("total_posts") ?: 0,24,!state.busy) {page=it}}
    }
}
