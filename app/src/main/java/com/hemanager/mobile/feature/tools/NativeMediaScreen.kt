package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hemanager.mobile.MediaItem
import com.hemanager.mobile.feature.library.openItem
import com.hemanager.mobile.ui.theme.HeColors
import org.json.JSONObject

@Composable internal fun NativeMediaRow(item: JSONObject, api: NativeApi, onClick: ()->Unit) {
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=HeColors.Panel)) {
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            val cover=item.text("cover_path")
            AsyncImage(model=ImageRequest.Builder(LocalContext.current).data(if(cover.isBlank()) null else api.origin+"/mobile/thumbnails/"+encoded(cover)).addHeader("Authorization","Bearer ${api.token}").build(),contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.size(72.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(item.text("title","未命名"),style=MaterialTheme.typography.titleSmall)
                Text("${mediaTypeName(item.text("media_type"))} · ${item.optInt("rating")} 星${if(item.optBoolean("favorite")) " · 已收藏" else ""}",color=HeColors.OpWhiteSoft)
                if(item.optBoolean("is_missing")) Text("文件缺失",color=MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable internal fun NativeMediaScreen(server: String, token: String, onBack: ()->Unit, onLogout: ()->Unit, initialFilter: String="") {
    val state=rememberNativeActions(server,token,onLogout)
    var records by remember { mutableStateOf<List<JSONObject>>(emptyList()) }; var total by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }; var applied by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }; var sort by rememberSaveable { mutableStateOf("date") }
    var tag by rememberSaveable { mutableStateOf("") }; var tagDraft by rememberSaveable { mutableStateOf("") }; var source by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }; var viewStatus by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }; var grid by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableIntStateOf(0) }
    suspend fun load() {
        val r=state.api.request(query("/media","limit" to 24,"offset" to page*24,"search" to applied,"media_type" to type,"sort" to sort,"tag" to tag,"source_site" to source,"view_status" to viewStatus,"favorite" to if(filter=="favorite") true else null,"is_missing" to if(filter=="missing") true else null))
        records=(r.value as org.json.JSONArray).objects();total=r.total ?: records.size
    }
    LaunchedEffect(page,applied,type,sort,tag,source,filter,viewStatus) { state.run { load() } }
    if(detail!=0) { NativeMediaDetail(server,token,detail,{detail=0;state.run {load()}},onLogout);return }
    NativeScene("媒体管理",state,onBack,{state.run {load()}}) {
        item { NativeSection("筛选") {
            NativeField("搜索标题",search,{search=it},enabled=!state.busy)
            Button(onClick={applied=search.trim();tag=tagDraft.trim();page=0},enabled=!state.busy) {Text("应用筛选")}
            NativeChoice("类型",type,listOf("" to "全部","video" to "视频","manga" to "漫画","image" to "杂图","audio" to "音频"),!state.busy) {type=it;page=0}
            TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "收起高级筛选" else "展开高级筛选")}
            if(advanced) {
            NativeChoice("范围",filter,listOf("" to "全部","favorite" to "收藏","missing" to "缺失文件"),!state.busy) {filter=it;page=0}
            NativeChoice("观看状态",viewStatus,listOf("" to "全部","unviewed" to "未看","viewing" to "在看","viewed" to "已看"),!state.busy) {viewStatus=it;page=0}
            NativeChoice("排序",sort,listOf("date" to "最新","title" to "标题","rating" to "评分","opened" to "最近打开"),!state.busy) {sort=it;page=0}
            NativeField("标签名称（点击应用筛选生效）",tagDraft,{tagDraft=it},enabled=!state.busy)
            NativeChoice("来源",source,listOf("" to "全部","local" to "本地","x" to "X","wnacg" to "WNACG","asmr" to "ASMR","pawchive" to "Pawchive"),!state.busy) {source=it;page=0}
            }
            Row { Checkbox(grid,{grid=it});Text("双列布局",Modifier.padding(top=12.dp)) }
        } }
        item { Text("共 $total 个媒体 · 第 ${page+1} 页",color=HeColors.OpWhiteSoft) }
        if(grid) items(records.chunked(2)) { row -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { row.forEach { m -> Box(Modifier.weight(1f)) { NativeMediaRow(m,state.api) {detail=m.optInt("id")} } };if(row.size==1) Spacer(Modifier.weight(1f)) } }
        else items(records,key={it.optInt("id")}) { m -> NativeMediaRow(m,state.api) {detail=m.optInt("id")} }
        if(records.isEmpty()&&!state.busy) item { Text("暂无符合条件的媒体") }
        item { NativePagination(page,total,24,!state.busy) {page=it} }
    }
}

@Composable internal fun NativePagination(page: Int,total: Int,size: Int,enabled: Boolean,onPage: (Int)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        OutlinedButton(onClick={onPage(page-1)},enabled=enabled&&page>0) {Text("上一页")}
        Text("${page+1} / ${maxOf(1,(total+size-1)/size)}",Modifier.padding(top=12.dp))
        OutlinedButton(onClick={onPage(page+1)},enabled=enabled&&(page+1)*size<total) {Text("下一页")}
    }
}

@Composable internal fun NativeMediaDetail(server: String,token: String,id: Int,onBack: ()->Unit,onLogout: ()->Unit) {
    val state=rememberNativeActions(server,token,onLogout);val context=LocalContext.current
    var media by remember(id) { mutableStateOf<JSONObject?>(null) }
    var title by rememberSaveable(id) { mutableStateOf("") };var duration by rememberSaveable(id) { mutableStateOf("") }
    var progress by rememberSaveable(id) { mutableStateOf("") };var sourceUrl by rememberSaveable(id) { mutableStateOf("") }
    var sourceSite by rememberSaveable(id) { mutableStateOf("") };var viewStatus by rememberSaveable(id) { mutableStateOf("unviewed") }
    var tag by rememberSaveable(id) {mutableStateOf("")}
    suspend fun load() {
        media=state.api.get("/media/$id")
        media?.let { title=it.text("title");duration=it.optInt("duration").toString();progress=it.optInt("progress").toString();sourceUrl=it.text("source_url");sourceSite=it.text("source_site");viewStatus=it.text("view_status","unviewed") }
    }
    LaunchedEffect(id) {state.run {load()}}
    NativeScene("媒体详情",state,onBack,{state.run {load()}}) {
        media?.let { m ->
            item { NativeMediaRow(m,state.api) {if(!state.busy&&!m.optBoolean("is_missing")) openItem(context,MediaItem.fromJson(m),server,token)} }
            item { NativeSection("观看与收藏") {
                Button(onClick={openItem(context,MediaItem.fromJson(m),server,token)},enabled=!state.busy&&!m.optBoolean("is_missing")) {Text("打开原生播放器 / 阅读器")}
                OutlinedButton(onClick={state.run {media=state.api.send("/media/$id",obj("favorite" to !m.optBoolean("favorite")),"PATCH")}},enabled=!state.busy) {Text(if(m.optBoolean("favorite")) "取消收藏" else "收藏")}
                NativeChoice("评分",m.optInt("rating").toString(),(0..5).map {it.toString() to if(it==0) "未评" else "$it 星"},!state.busy) {r->state.run {media=state.api.send("/media/$id",obj("rating" to r.toInt()),"PATCH")}}
            } }
            item { NativeSection("编辑信息") {
                NativeField("标题",title,{title=it},enabled=!state.busy)
                NativeField("时长（秒）",duration,{duration=it},enabled=!state.busy)
                NativeField("进度（视频秒 / 漫画页）",progress,{progress=it},enabled=!state.busy)
                NativeChoice("观看状态",viewStatus,listOf("unviewed" to "未看","viewing" to "在看","viewed" to "已看"),!state.busy) {viewStatus=it}
                NativeField("来源网址",sourceUrl,{sourceUrl=it},enabled=!state.busy)
                NativeField("来源站点",sourceSite,{sourceSite=it},enabled=!state.busy)
                Button(onClick={state.run {
                    require(title.isNotBlank()) {"标题不能为空"};val d=duration.toIntOrNull();val p=progress.toIntOrNull();require(d!=null&&d>=0&&p!=null&&p>=0) {"时长和进度必须是非负整数"}
                    media=state.api.send("/media/$id",obj("title" to title.trim(),"duration" to d,"progress" to p,"view_status" to viewStatus,"source_url" to sourceUrl.trim(),"source_site" to sourceSite.trim()),"PATCH");state.message="信息已保存"
                }},enabled=!state.busy) {Text("保存信息")}
            } }
            item { NativeSection("标签") {
                m.optJSONArray("tags").objects().forEach { t -> Row(Modifier.fillMaxWidth()) {
                    Text(t.text("name"),Modifier.weight(1f).padding(top=12.dp))
                    TextButton(onClick={state.confirm("移除标签“${t.text("name")}”？") {media=state.api.request("/media/$id/tags/${t.optInt("id")}","DELETE").value as JSONObject}},enabled=!state.busy) {Text("移除")}
                } }
                NativeField("新标签",tag,{tag=it},enabled=!state.busy)
                Button(onClick={state.run {media=state.api.send("/media/$id/tags",obj("name" to tag.trim()));tag=""}},enabled=!state.busy&&tag.isNotBlank()) {Text("添加标签")}
            } }
            item { NativeSection("维护") {
                Text(m.text("absolute_path",m.text("relative_path")),color=HeColors.OpWhiteSoft)
                OutlinedButton(onClick={state.run {state.api.send("/media/$id/recheck");load();state.message="已重新检查文件"}},enabled=!state.busy) {Text("重新检查文件")}
                OutlinedButton(onClick={state.run {state.api.send("/media/$id/regenerate-thumbnail");load();state.message="缩略图已更新"}},enabled=!state.busy) {Text("重新生成缩略图")}
                TextButton(onClick={state.confirm("删除“${m.text("title")}”的媒体记录？此操作无法撤销。") {state.api.request("/media/$id","DELETE");onBack()}},enabled=!state.busy) {Text("删除媒体记录",color=MaterialTheme.colorScheme.error)}
            } }
        }
    }
}
