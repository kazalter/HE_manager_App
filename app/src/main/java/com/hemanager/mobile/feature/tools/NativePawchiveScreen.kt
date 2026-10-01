package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.hemanager.mobile.ui.theme.HeColors
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun NativePawchiveScreen(server: String,token: String,onBack: ()->Unit,onLogout: ()->Unit) {
    val state=rememberNativeActions(server,token,onLogout)
    var connected by remember {mutableStateOf(false)};var favorites by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var username by rememberSaveable {mutableStateOf("")};var password by remember {mutableStateOf("")}
    var search by rememberSaveable {mutableStateOf("")};var applied by rememberSaveable {mutableStateOf("")}
    var advancedSearch by rememberSaveable {mutableStateOf(false)}
    var service by rememberSaveable {mutableStateOf("")};var creatorId by rememberSaveable {mutableStateOf("")}
    var tag by rememberSaveable {mutableStateOf("")};var type by rememberSaveable {mutableStateOf("all")}
    var posts by remember {mutableStateOf<List<JSONObject>>(emptyList())};var cursor by remember {mutableStateOf("")};var more by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<Set<String>>(emptySet())};var opened by remember {mutableStateOf<JSONObject?>(null)}
    var jobs by remember {mutableStateOf<List<JSONObject>>(emptyList())};var tab by rememberSaveable {mutableStateOf("browse")}
    var expandedJob by rememberSaveable {mutableStateOf("")};var jobDetail by remember {mutableStateOf<JSONObject?>(null)}
    suspend fun loadFavorites() {favorites=if(connected) state.api.get("/external/pawchive/account/favorites").optJSONArray("items").objects() else emptyList()}
    suspend fun loadJobs() {
        jobs=state.api.get("/external/pawchive/downloads").optJSONArray("items").objects()
        if(expandedJob.isNotBlank()) jobDetail=state.api.get("/external/pawchive/downloads/${encoded(expandedJob)}")
    }
    suspend fun browse(append: Boolean=false) {
        if(creatorId.isBlank()&&applied.length<3) {posts=emptyList();cursor="";more=false;return}
        val response=state.api.get(query("/external/pawchive/posts","q" to applied,"service" to service,"creator_id" to creatorId,"tag" to if(creatorId.isNotBlank()) tag else "","media_type" to type,"cursor" to if(append) cursor else ""))
        val fresh=response.optJSONArray("items").objects()
        posts=(if(append) posts+fresh else fresh).distinctBy {it.text("post_key")};cursor=response.text("next_cursor");more=response.optBoolean("has_more")
        state.message=response.optJSONArray("warnings")?.let {a->(0 until a.length()).joinToString("\n") {a.optString(it)}}.orEmpty()
        if(!append) selected=emptySet()
    }
    suspend fun refresh() {connected=state.api.get("/external/pawchive/account/status").optBoolean("connected");loadJobs();loadFavorites();if(posts.isNotEmpty()) browse()}
    fun selection(post: JSONObject,keys: List<String>?=null)=obj("service" to post.text("service"),"creator_id" to post.text("creator_id"),"post_id" to post.text("post_id"),"attachment_keys" to keys?.let {JSONArray(it)})
    fun download(selections: List<JSONObject>) {state.run {
        val body=obj("selections" to JSONArray(selections));val preview=state.api.send("/external/pawchive/downloads/preview",body)
        val count=preview.optInt("supported")-preview.optInt("already_downloaded")
        require(count>0) {"所选内容没有待下载的受支持附件"}
        state.confirm("将 ${preview.optInt("selected_posts")} 篇帖子中的 $count 个附件下载入库；跳过 ${preview.optInt("unsupported")} 个不支持的附件。") {
            state.api.send("/external/pawchive/downloads",body);selected=emptySet();loadJobs();state.message="下载任务已提交"
        }
    }}
    LaunchedEffect(Unit) {state.run {refresh()}}
    NativePoll(tab,tab=="downloads"&&jobs.any {activeJob(it)}) {try {loadJobs()} catch(e: CancellationException) {throw e} catch(e: Exception) {state.error=e.message ?: "下载任务更新失败"}}
    if(opened!=null) {
        NativePawchivePost(state,opened!!,type,{opened=null},{p,keys->download(listOf(selection(p,keys)))})
        return
    }
    NativeScene("Pawchive",state,onBack,{state.run {refresh()}}) {
        item {NativeChoice("页面",tab,listOf("browse" to "作者与帖子","downloads" to "下载任务"),!state.busy) {tab=it}}
        if(tab=="browse") {
            item {NativeSection("Pawchive 账号") {
                Text(if(connected) "账号已连接" else "连接账号后可读取和管理喜欢的作者")
                if(!connected) {
                    NativeField("用户名",username,{username=it},enabled=!state.busy)
                    NativeField("密码",password,{password=it},secret=true,enabled=!state.busy)
                    Button(onClick={state.run {try {state.api.send("/external/pawchive/account/login",obj("username" to username,"password" to password));connected=true;loadFavorites();username=""} finally {password=""}}},enabled=!state.busy&&username.isNotBlank()&&password.isNotBlank()) {Text("连接账号")}
                } else OutlinedButton(onClick={state.confirm("退出 Pawchive 账号？") {state.api.request("/external/pawchive/account/session","DELETE");connected=false;favorites=emptyList()}},enabled=!state.busy) {Text("退出 Pawchive")}
            } }
            item {NativeSection("查找作者与帖子") {
                NativeField("作者、关键词或作者网址",search,{search=it},enabled=!state.busy)
                Text("关键词至少 3 个字符；为空时仅展示喜欢的作者。",color=HeColors.OpWhiteSoft)
                TextButton(onClick={advancedSearch=!advancedSearch}) {Text(if(advancedSearch) "收起作者筛选" else "按服务和作者 ID 查找")}
                if(advancedSearch) {
                NativeField("服务（直接查找作者时填写）",service,{service=it},enabled=!state.busy)
                NativeField("作者 ID",creatorId,{creatorId=it},enabled=!state.busy)
                if(creatorId.isNotBlank()) NativeField("作者标签",tag,{tag=it},enabled=!state.busy)
                }
                NativeChoice("附件类型",type,listOf("all" to "全部","image" to "图片","video" to "视频"),!state.busy) {type=it}
                Button(onClick={state.run {
                    val url=android.net.Uri.parse(search.trim());val segments=url.pathSegments
                    if(segments.size>=3&&segments[1]=="user") {service=segments[0];creatorId=segments[2];applied=""} else applied=search.trim()
                    require((creatorId.isBlank()&&service.isBlank())||(creatorId.isNotBlank()&&service.isNotBlank())) {"请同时填写服务和作者 ID"}
                    require(applied.isBlank()||applied.length>=3) {"关键词至少 3 个字符"};browse()
                }},enabled=!state.busy) {Text("搜索")}
                if(creatorId.isNotBlank()||posts.isNotEmpty()) TextButton(onClick={service="";creatorId="";applied="";search="";tag="";posts=emptyList();selected=emptySet();more=false}) {Text("返回喜欢的作者")}
            } }
            if(creatorId.isBlank()) {
                val creators=if(posts.isEmpty()) favorites else posts.map {obj("service" to it.text("service"),"creator_id" to it.text("creator_id"),"creator_name" to it.text("creator_name",it.text("creator_id")))}.distinctBy {it.text("service")+":"+it.text("creator_id")}
                item {Text(if(posts.isEmpty()) "喜欢的作者 · ${favorites.size}" else "搜索结果中的作者 · ${creators.size}",style=MaterialTheme.typography.titleMedium)}
                items(creators,key={it.text("service")+":"+it.text("creator_id")}) {c->NativeSection(c.text("creator_name",c.text("creator_id"))) {
                    Text("${c.text("service")} / ${c.text("creator_id")}",color=HeColors.OpWhiteSoft)
                    Button(onClick={state.run {service=c.text("service");creatorId=c.text("creator_id");applied="";tag="";browse()}},enabled=!state.busy) {Text("浏览帖子")}
                    val favorite=favorites.any {it.text("service")==c.text("service")&&it.text("creator_id")==c.text("creator_id")}
                    OutlinedButton(onClick={state.run {state.api.request("/external/pawchive/account/favorites/${encoded(c.text("service"))}/${encoded(c.text("creator_id"))}",if(favorite) "DELETE" else "PUT");loadFavorites()}},enabled=!state.busy&&connected) {Text(if(favorite) "取消喜欢" else "喜欢作者")}
                } }
            }
            if(creatorId.isNotBlank()) item {NativeSection("作者操作") {
                val favorite=favorites.any {it.text("service")==service&&it.text("creator_id")==creatorId}
                OutlinedButton(onClick={state.run {state.api.request("/external/pawchive/account/favorites/${encoded(service)}/${encoded(creatorId)}",if(favorite) "DELETE" else "PUT");loadFavorites()}},enabled=!state.busy&&connected) {Text(if(favorite) "取消喜欢作者" else "喜欢作者")}
                if(selected.isNotEmpty()) Button(onClick={download(posts.filter {it.text("post_key") in selected}.map {selection(it)})},enabled=!state.busy) {Text("下载选中的 ${selected.size} 篇帖子")}
            } }
            items(posts,key={it.text("post_key")}) {post->NativeSection(post.text("title","帖子")) {
                Text("${post.text("creator_name",post.text("creator_id"))} · ${post.text("published_at")}",color=HeColors.OpWhiteSoft)
                Row {Checkbox(post.text("post_key") in selected,{yes->selected=if(yes) (selected+post.text("post_key")).take(20).toSet() else selected-post.text("post_key")},enabled=!state.busy);Text("选择下载",Modifier.padding(top=12.dp))}
                Button(onClick={state.run {opened=state.api.get("/external/pawchive/posts/${encoded(post.text("service"))}/${encoded(post.text("creator_id"))}/${encoded(post.text("post_id"))}")}},enabled=!state.busy) {Text("打开附件")}
                Text("附件 ${post.optInt("reported_attachment_count")}",color=HeColors.OpWhiteSoft)
            } }
            if(more) item {Button(onClick={state.run {browse(true)}},enabled=!state.busy) {Text("加载更多")}}
        } else {
            if(jobs.isEmpty()&&!state.busy) item {Text("暂无下载任务")}
            items(jobs,key={it.text("job_id")}) {j->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                JobStatus(j)
                NativeSection("附件状态") {
                    TextButton(onClick={state.run {expandedJob=j.text("job_id");jobDetail=state.api.get("/external/pawchive/downloads/${encoded(expandedJob)}")}},enabled=!state.busy) {Text("查看附件状态")}
                    (if(expandedJob==j.text("job_id")) jobDetail else j)?.optJSONArray("attachments").objects().forEach {a->Text("${a.text("filename")} · ${statusName(a.text("status"))}${if(a.text("error").isNotBlank()) "\n${a.text("error")}" else ""}")}
                    if(activeJob(j)) OutlinedButton(onClick={state.confirm("取消此下载任务？") {state.api.send("/external/pawchive/downloads/${encoded(j.text("job_id"))}/cancel");loadJobs()}},enabled=!state.busy) {Text("取消任务")}
                    else if(j.text("status") in setOf("failed","canceled","interrupted")) Button(onClick={state.run {state.api.send("/external/pawchive/downloads/${encoded(j.text("job_id"))}/retry");loadJobs()}},enabled=!state.busy) {Text("重试未完成附件")}
                }
            } }
        }
    }
}

@Composable private fun NativePawchivePost(state: NativeActions,post: JSONObject,type: String,onBack: ()->Unit,download: (JSONObject,List<String>?)->Unit) {
    val attachments=post.optJSONArray("attachments").objects().filter {it.text("availability")=="playable"&&(type=="all"||it.text("media_type")==type)}
    var selected by remember {mutableStateOf<Set<String>>(emptySet())};var viewer by rememberSaveable {mutableIntStateOf(-1)}
    NativeScene(post.text("title","帖子附件"),state,onBack,{}) {
        item {Text("${post.text("creator_name")} · ${attachments.size} 个可播放附件")}
        item {Button(onClick={download(post,if(selected.isEmpty()) null else selected.toList())},enabled=!state.busy) {Text(if(selected.isEmpty()) "下载整篇帖子" else "下载选中的 ${selected.size} 个附件")}}
        items(attachments,key={it.text("attachment_key")}) {a->NativeSection(a.text("filename")) {
            val ref=a.text("preview_ref",a.text("stream_ref"))
            AsyncImage(model=ImageRequest.Builder(LocalContext.current).data(state.api.origin+"/external/pawchive/media/"+encoded(ref)).addHeader("Authorization","Bearer ${state.api.token}").build(),contentDescription=a.text("filename"),contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(180.dp))
            Row {Checkbox(a.text("attachment_key") in selected,{yes->selected=if(yes) selected+a.text("attachment_key") else selected-a.text("attachment_key")},enabled=!state.busy);Text("选择下载",Modifier.padding(top=12.dp))}
            Button(onClick={viewer=attachments.indexOf(a)},enabled=!state.busy) {Text(if(a.text("media_type")=="video") "播放视频" else "全屏查看")}
        } }
        post.optJSONArray("attachments").objects().filter {it.text("availability")!="playable"}.forEach {a->item {Text("${a.text("filename")} · 暂不支持预览",color=HeColors.OpWhiteSoft)}}
    }
    if(viewer>=0&&attachments.isNotEmpty()) NativeAttachmentViewer(state.api,attachments,viewer,{viewer=it},{viewer=-1})
}

@Composable internal fun NativeAttachmentViewer(api: NativeApi,attachments: List<JSONObject>,index: Int,onIndex: (Int)->Unit,onClose: ()->Unit) {
    val context=LocalContext.current;var controls by remember {mutableStateOf(true)}
    var autoplay by rememberSaveable {mutableStateOf(false)};var interval by rememberSaveable {mutableStateOf("5")}
    var scale by remember(index) {mutableFloatStateOf(1f)};var x by remember(index) {mutableFloatStateOf(0f)};var y by remember(index) {mutableFloatStateOf(0f)}
    val a=attachments[index];val path="/external/pawchive/media/"+encoded(a.text("stream_ref"))
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(index) {attachments.drop(index+1).take(6).filter {it.text("media_type")=="image"}.forEach {next->context.imageLoader.enqueue(ImageRequest.Builder(context).data(api.origin+"/external/pawchive/media/"+encoded(next.text("stream_ref"))).addHeader("Authorization","Bearer ${api.token}").build())}}
    LaunchedEffect(index,autoplay,interval) {if(autoplay&&a.text("media_type")=="image") owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {delay((interval.toLongOrNull() ?: 5).coerceIn(2,60)*1000);if(index+1<attachments.size) onIndex(index+1) else autoplay=false}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(color=HeColors.Ink,modifier=Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                if(controls) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton(onClick=onClose) {Text("关闭")};Text("${index+1} / ${attachments.size}",Modifier.padding(12.dp));TextButton(onClick={scale=1f;x=0f;y=0f}) {Text("重置缩放")}}
                Box(Modifier.weight(1f).fillMaxWidth().pointerInput(index) {detectTapGestures(onTap={controls=!controls})}) {
                    if(a.text("media_type")=="video") NativeVideo(api,path,Modifier.fillMaxSize(),autoplay) {if(autoplay&&index+1<attachments.size) onIndex(index+1)}
                    else AsyncImage(model=ImageRequest.Builder(context).data(api.origin+path).addHeader("Authorization","Bearer ${api.token}").build(),contentDescription=a.text("filename"),contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().pointerInput(index) {detectTransformGestures {_,pan,zoom,_->scale=(scale*zoom).coerceIn(1f,5f);x=if(scale==1f) 0f else x+pan.x;y=if(scale==1f) 0f else y+pan.y}}.graphicsLayer(scaleX=scale,scaleY=scale,translationX=x,translationY=y))
                }
                if(controls) Column(Modifier.padding(16.dp)) {
                    Text(a.text("filename"))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        OutlinedButton(onClick={onIndex(index-1)},enabled=index>0) {Text("上一张")}
                        OutlinedButton(onClick={onIndex(index+1)},enabled=index+1<attachments.size) {Text("下一张")}
                    }
                    Row {Checkbox(autoplay,{autoplay=it});Text("自动播放",Modifier.padding(top=12.dp))}
                    NativeField("图片间隔（2–60 秒）",interval,{interval=it})
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable internal fun NativeVideo(api: NativeApi,path: String,modifier: Modifier,autoplay: Boolean=false,onEnded: ()->Unit={}) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current;val ended by rememberUpdatedState(onEnded)
    var error by remember(path) {mutableStateOf("")}
    val player=remember(api.origin,path) {ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${api.token}")))).build().apply {setMediaItem(MediaItem.fromUri(api.origin+path));prepare();playWhenReady=autoplay}}
    DisposableEffect(player) {
        val listener=object: Player.Listener {override fun onPlaybackStateChanged(state: Int) {if(state==Player.STATE_ENDED) ended()};override fun onPlayerError(e: androidx.media3.common.PlaybackException) {error="播放失败：${e.errorCodeName}"}}
        player.addListener(listener);onDispose {player.removeListener(listener);player.release()}
    }
    DisposableEffect(owner,player) {val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE) player.pause()};owner.lifecycle.addObserver(observer);onDispose {owner.lifecycle.removeObserver(observer)}}
    Box(modifier) {AndroidView(factory={PlayerView(it).apply {this.player=player}},modifier=Modifier.fillMaxSize(),onRelease={it.player=null});if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)}
}
