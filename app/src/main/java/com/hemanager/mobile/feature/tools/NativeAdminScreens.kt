package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.ui.theme.HeColors
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun NativeUsersScreen(server: String,token: String,onBack: ()->Unit,onLogout: ()->Unit) {
    val state=rememberNativeActions(server,token,onLogout)
    var users by remember {mutableStateOf<List<JSONObject>>(emptyList())};var me by remember {mutableIntStateOf(0)}
    var editor by remember {mutableStateOf(false)};var editing by remember {mutableStateOf<JSONObject?>(null)}
    var username by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
    var admin by remember {mutableStateOf(false)};var active by remember {mutableStateOf(true)}
    suspend fun load() {me=state.api.get("/auth/me").optInt("id");users=state.api.list("/users")}
    fun edit(user: JSONObject?) {editing=user;username=user?.text("username").orEmpty();password="";admin=user?.optBoolean("is_admin") ?: false;active=user?.optBoolean("is_active") ?: true;editor=true;state.error=""}
    LaunchedEffect(Unit) {state.run {load()}}
    NativeScene("用户管理",state,onBack,{state.run {load()}}) {
        item {Button(onClick={edit(null)},enabled=!state.busy) {Text("添加用户")}}
        items(users,key={it.optInt("id")}) {u->NativeSection(u.text("username")) {
            Text("${if(u.optBoolean("is_admin")) "管理员" else "普通用户"} · ${if(u.optBoolean("is_active")) "已启用" else "已停用"}${if(u.optInt("id")==me) " · 当前账号" else ""}")
            Text("创建时间 ${u.text("created_at")}",color=HeColors.OpWhiteSoft)
            OutlinedButton(onClick={edit(u)},enabled=!state.busy) {Text("编辑账号")}
            TextButton(onClick={state.confirm("删除用户“${u.text("username")}”？此操作无法撤销。") {state.api.request("/users/${u.optInt("id")}","DELETE");load()}},enabled=!state.busy&&u.optInt("id")!=me) {Text("删除用户",color=MaterialTheme.colorScheme.error)}
        } }
    }
    if(editor) AlertDialog(onDismissRequest={if(!state.busy) {editor=false;password=""}},title={Text(if(editing==null) "添加用户" else "编辑账号")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        NativeField("用户名（3–40 字符）",username,{username=it},enabled=!state.busy)
        NativeField(if(editing==null) "密码（10–128 字符）" else "新密码（留空保留）",password,{password=it},secret=true,enabled=!state.busy)
        Row {Checkbox(admin,{admin=it},enabled=!state.busy&&editing?.optInt("id")!=me);Text("管理员",Modifier.padding(top=12.dp))}
        if(editing!=null) Row {Checkbox(active,{active=it},enabled=!state.busy&&editing?.optInt("id")!=me);Text("启用账号",Modifier.padding(top=12.dp))}
        if(state.error.isNotBlank()) Text(state.error,color=MaterialTheme.colorScheme.error)
        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={TextButton(onClick={state.run {
        require(username.trim().length in 3..40) {"用户名必须为 3–40 个字符"}
        require((editing!=null&&password.isBlank())||password.length in 10..128) {"密码必须为 10–128 个字符"}
        val body=obj("username" to username.trim(),"is_admin" to admin,"is_active" to active,"password" to password.ifBlank {null})
        state.api.send(if(editing==null) "/users" else "/users/${editing!!.optInt("id")}",body,if(editing==null) "POST" else "PUT")
        password="";editor=false;load();state.message="账号已保存"
    }},enabled=!state.busy) {Text("保存")}},dismissButton={TextButton(onClick={editor=false;password=""},enabled=!state.busy) {Text("取消")}})
}

@Composable internal fun NativeServerScreen(server: String,token: String,onBack: ()->Unit,onLogout: ()->Unit) {
    val state=rememberNativeActions(server,token,onLogout)
    var folders by remember {mutableStateOf<List<JSONObject>>(emptyList())};var proxy by rememberSaveable {mutableStateOf("")}
    var path by rememberSaveable {mutableStateOf("")};var mode by rememberSaveable {mutableStateOf("auto")}
    var thumbs by rememberSaveable {mutableStateOf(true)};var interval by rememberSaveable {mutableStateOf("1")}
    var matches by remember {mutableStateOf<List<String>>(emptyList())};var add by rememberSaveable {mutableStateOf(false)}
    suspend fun load() {folders=state.api.list("/folders");proxy=state.api.get("/external/proxy").text("proxy")}
    LaunchedEffect(Unit) {state.run {load()}}
    NativePoll(Unit,folders.any {it.text("status")=="scanning"}) {try {folders=state.api.list("/folders")} catch(e: CancellationException) {throw e} catch(e: Exception) {state.error=e.message ?: "扫描状态更新失败"}}
    NativeScene("服务器设置",state,onBack,{state.run {load()}}) {
        item {NativeSection("媒体目录") {
            Text("填写服务器上的目录路径。添加或更新配置后会开始扫描。",color=HeColors.OpWhiteSoft)
            Button(onClick={add=!add},enabled=!state.busy) {Text(if(add) "收起目录配置" else "添加 / 更新目录")}
            if(add) {
                NativeField("服务器绝对路径",path,{path=it},enabled=!state.busy)
                OutlinedButton(onClick={state.run {val a=state.api.get(query("/search-folder","name" to path.substringAfterLast('/'))).optJSONArray("results");matches=(0 until (a?.length() ?: 0)).map {a!!.optString(it)};if(matches.isEmpty()) state.message="未找到同名目录，请输入完整路径"}},enabled=!state.busy&&path.isNotBlank()) {Text("查找服务器目录")}
                matches.forEach {m->TextButton(onClick={path=m;matches=emptyList()}) {Text(m)}}
                NativeChoice("扫描类型",mode,listOf("auto" to "自动","video" to "视频","manga" to "漫画","image" to "杂图","audio" to "音频"),!state.busy) {mode=it}
                Row {Checkbox(thumbs,{thumbs=it},enabled=!state.busy);Text("生成缩略图",Modifier.padding(top=12.dp))}
                NativeField("缩略图间隔（1–60）",interval,{interval=it},enabled=!state.busy)
                Button(onClick={state.confirm("保存目录“$path”的扫描设置并开始扫描？") {
                    val n=interval.toIntOrNull();require(path.isNotBlank()&&n!=null&&n in 1..60) {"请填写目录，并将间隔设为 1–60"}
                    state.api.send("/folders",obj("path" to path.trim(),"scan_mode" to mode,"thumbnail_enabled" to thumbs,"thumbnail_interval" to n));load();add=false;state.message="目录已保存，扫描已提交"
                }},enabled=!state.busy&&path.isNotBlank()) {Text("保存并扫描")}
            }
            OutlinedButton(onClick={state.confirm("扫描所有已配置目录？") {state.api.send("/folders/scan-all");load();state.message="已提交全部目录扫描"}},enabled=!state.busy&&folders.none {it.text("status")=="scanning"}&&folders.isNotEmpty()) {Text("扫描全部目录")}
        } }
        items(folders,key={it.optInt("id")}) {f->NativeSection(f.text("path")) {
            Text("${f.text("scan_mode")} · ${statusName(f.text("status"))}\n上次扫描 ${f.text("last_scanned_at","尚未扫描")}",color=HeColors.OpWhiteSoft)
            OutlinedButton(onClick={path=f.text("path");mode=f.text("scan_mode","auto");thumbs=f.optBoolean("thumbnail_enabled",true);interval=f.optInt("thumbnail_interval",1).toString();add=true},enabled=!state.busy) {Text("编辑目录配置")}
            Button(onClick={state.run {state.api.send("/folders/${f.optInt("id")}/scan");load();state.message="扫描已提交"}},enabled=!state.busy&&f.text("status")!="scanning") {Text("扫描此目录")}
            TextButton(onClick={state.confirm("从库中移除目录“${f.text("path")}”及其媒体记录？硬盘文件将保留。") {state.api.request("/folders/${f.optInt("id")}","DELETE");load()}},enabled=!state.busy&&f.text("status")!="scanning") {Text("移除目录",color=MaterialTheme.colorScheme.error)}
        } }
        item {NativeSection("外部源代理") {
            NativeField("代理 URL（留空关闭）",proxy,{proxy=it},enabled=!state.busy)
            Button(onClick={state.run {state.api.send("/external/proxy",obj("proxy" to proxy.trim()),"PATCH");state.message="代理设置已保存"}},enabled=!state.busy) {Text("保存代理设置")}
        } }
        item {NativeSection("库维护") {
            OutlinedButton(onClick={state.confirm("重新检查库中的缺失文件？") {val result=state.api.send("/system/recheck-missing");state.message="缺失检查完成：${result.optInt("checked")} 个记录"}},enabled=!state.busy) {Text("重新检查缺失文件")}
            OutlinedButton(onClick={state.confirm("检查并补全 ASMR 封面？") {val result=state.api.send("/external/asmr/recheck-covers");state.message="检查 ${result.optInt("checked")}，修复 ${result.optInt("fixed")}，失败 ${result.optInt("failed")}"}},enabled=!state.busy) {Text("补全 ASMR 封面")}
        } }
    }
}

@Composable internal fun NativeDedupScreen(server: String,token: String,onBack: ()->Unit,onLogout: ()->Unit) {
    val state=rememberNativeActions(server,token,onLogout)
    var summary by remember {mutableStateOf<JSONObject?>(null)};var pairs by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var status by rememberSaveable {mutableStateOf("pending")};var level by rememberSaveable {mutableStateOf("")};var type by rememberSaveable {mutableStateOf("")}
    var sort by rememberSaveable {mutableStateOf("confidence")};var page by rememberSaveable {mutableIntStateOf(0)};var total by remember {mutableIntStateOf(0)}
    var selected by remember {mutableStateOf<Set<Int>>(emptySet())};var detail by rememberSaveable {mutableIntStateOf(0)}
    suspend fun load() {
        summary=state.api.get("/dedup/summary")
        val response=state.api.get(query("/dedup/candidates-page","status" to status,"level" to level,"media_type" to type,"sort" to sort,"limit" to 12,"offset" to page*12))
        pairs=response.optJSONArray("items").objects();total=response.optInt("total");selected=selected.intersect(pairs.filter {it.text("status")=="pending"}.map {it.optInt("id")}.toSet())
    }
    LaunchedEffect(status,level,type,sort,page) {state.run {load()}}
    NativePoll(Unit,(summary?.optInt("queue_size") ?: 0)>0||(summary?.optInt("checking") ?: 0)>0) {try {summary=state.api.get("/dedup/summary")} catch(e: CancellationException) {throw e} catch(e: Exception) {state.error=e.message ?: "队列状态更新失败"}}
    if(detail>0) {NativeMediaDetail(server,token,detail,{detail=0},onLogout);return}
    NativeScene("重复管理",state,onBack,{state.run {load()}}) {
        summary?.let {s->item {NativeSection("检测队列") {
            Text("待处理 ${s.optInt("pending_pairs")} · 检查中 ${s.optInt("checking")} · 队列 ${s.optInt("queue_size")}")
            Text("高置信 ${s.optInt("strong_duplicate")} · 中置信 ${s.optInt("suspected_duplicate")} · 低置信 ${s.optInt("weak_suspected")}",color=HeColors.OpWhiteSoft)
        } } }
        item {NativeSection("筛选") {
            NativeChoice("处理状态",status,listOf("pending" to "待处理","all" to "全部","merged" to "已保留","replaced" to "已替换","kept_both" to "非重复","ignored" to "已忽略"),!state.busy) {status=it;page=0}
            NativeChoice("置信度",level,listOf("" to "全部","strong_duplicate" to "高","suspected_duplicate" to "中","weak_suspected" to "低"),!state.busy) {level=it;page=0}
            NativeChoice("媒体类型",type,listOf("" to "全部","video" to "视频","manga" to "漫画","image" to "图片","audio" to "音频"),!state.busy) {type=it;page=0}
            NativeChoice("排序",sort,listOf("confidence" to "置信度","newest" to "最新","oldest" to "最早"),!state.busy) {sort=it;page=0}
            if(selected.isNotEmpty()) listOf("keep_both" to "批量标记非重复","ignore" to "批量忽略").forEach {(action,label)->OutlinedButton(onClick={state.confirm("$label ${selected.size} 组？") {state.api.send("/dedup/candidates-batch-resolve",obj("pair_ids" to JSONArray(selected.toList()),"action" to action));selected=emptySet();load()}},enabled=!state.busy) {Text(label)}}
        } }
        if(pairs.isEmpty()&&!state.busy) item {Text("暂无重复候选")}
        items(pairs,key={it.optInt("id")}) {pair->NativeSection("${pair.optInt("similarity")}% 相似 · ${pair.text("level")} · ${pair.text("status")}") {
            Text(pair.text("reason"),color=HeColors.OpWhiteSoft)
            if(pair.text("status")=="pending") Row {Checkbox(pair.optInt("id") in selected,{yes->selected=if(yes) selected+pair.optInt("id") else selected-pair.optInt("id")},enabled=!state.busy);Text("选择批量处理",Modifier.padding(top=12.dp))}
            listOf("existing" to "原记录","candidate" to "候选记录").forEach {(key,label)->pair.optJSONObject(key)?.let {m->
                Text(label,style=MaterialTheme.typography.labelLarge)
                NativeMediaRow(m,state.api) {detail=m.optInt("id")}
                Text("${m.text("display_path")}\n${m.optLong("file_size")} 字节 · ${m.optInt("width")}×${m.optInt("height")} · ${m.optInt("duration")} 秒 · ${m.optInt("page_count")} 页",color=HeColors.OpWhiteSoft)
                OutlinedButton(onClick={state.run {state.api.send("/dedup/media/${m.optInt("id")}/recheck");load();state.message="已提交重新检测"}},enabled=!state.busy) {Text("重新检测此记录")}
                TextButton(onClick={state.confirm("永久删除“${m.text("title")}”在磁盘上的文件？此操作无法撤销。") {state.api.request("/dedup/media/${m.optInt("id")}/file","DELETE",obj("confirm" to true));load()}},enabled=!state.busy&&!m.optBoolean("is_missing")) {Text("删除磁盘文件",color=MaterialTheme.colorScheme.error)}
            } }
            if(pair.text("status")=="pending") listOf("keep_existing" to "保留原记录","replace_path" to "采用候选路径","keep_both" to "两者不是重复","ignore" to "忽略此组").forEach {(action,label)->
                OutlinedButton(onClick={state.confirm("$label？${if(action=="replace_path") "原记录将采用候选路径，候选记录会被合并移除。" else if(action=="keep_existing") "候选记录会被隐藏。" else ""}") {state.api.send("/dedup/candidates/${pair.optInt("id")}/resolve",obj("action" to action));load()}},enabled=!state.busy) {Text(label)}
            }
        } }
        item {NativePagination(page,total,12,!state.busy) {page=it}}
    }
}
