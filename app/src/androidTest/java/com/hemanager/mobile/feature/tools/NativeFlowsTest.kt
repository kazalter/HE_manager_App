package com.hemanager.mobile.feature.tools

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

/** These tests refuse to run against the real package or any production host. */
@RunWith(AndroidJUnit4::class)
class NativeFlowsTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=UiDevice.getInstance(instrumentation)
    @Before fun openQa() {
        check(context.packageName=="com.hemanager.mobile.qa") {"Use tests/qa-package.gradle; never run mutations on the real app"}
        context.getSharedPreferences("he_manager",0).edit().putString("server_url","http://127.0.0.1:18010").putString("token","qa-token").commit()
        instrumentation.startActivitySync(Intent().setClassName(context.packageName,"com.hemanager.mobile.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.waitForIdleSync()
        device.waitForIdle(1500)
        check(device.wait(Until.hasObject(By.text("工具")),15000)) {"Library navigation did not load"}
        tap("工具")
        check(device.wait(Until.hasObject(By.text("发现与工具")),10000)) {"Tools did not open"}
    }
    private fun element(label: String): UiObject2 {
        // API responses may create a dialog after Compose has already become idle.
        device.wait(Until.hasObject(By.text(label)),2500)
        for(direction in listOf(false,true)) repeat(14) {
            val found=device.findObject(By.text(label)) ?: device.findObject(By.desc(label))
            if(found!=null&&found.visibleBounds.height()>0) return found
            val x=device.displayWidth/2;val low=device.displayHeight*4/5;val high=device.displayHeight/4
            device.swipe(x,if(direction) high else low,x,if(direction) low else high,80)
            android.os.SystemClock.sleep(500) // Let native list motion settle before using accessibility bounds.
            device.waitForIdle(1500)
        }
        device.dumpWindowHierarchy(java.io.File(context.filesDir,"qa-hierarchy.xml"))
        error("UI missing: $label")
    }
    private fun tap(label: String) {
        val node=element(label)
        println("QA tap $label at ${node.visibleBounds}")
        node.click();device.waitForIdle(1500)
    }
    private fun field(label: String,value: String) {
        val labelNode=element(label)
        val editor=if(labelNode.className=="android.widget.EditText") labelNode else labelNode.parent
        check(editor.className=="android.widget.EditText") {"Not an editor: $label"}
        editor.text=value;device.waitForIdle(500)
        if(device.findObject(By.pkg("com.sohu.inputmethod.sogou"))!=null) device.pressBack()
    }
    private fun events(): JSONArray = JSONArray(URL("http://127.0.0.1:18010/events").readText())
    private fun expect(path: String,predicate: (org.json.JSONObject)->Boolean={true}) {
        val until=System.currentTimeMillis()+6000
        do {
            val e=events()
            if((0 until e.length()).any {e.getJSONObject(it).optString("path")==path&&predicate(e.getJSONObject(it).getJSONObject("body"))}) return
            Thread.sleep(150)
        } while(System.currentTimeMillis()<until)
        fail("Expected API mutation $path")
    }
    @Test fun mediaEditingAndPagination() {
        tap("媒体管理");tap("QA media 01")
        field("标题","本地编辑验证")
        tap("保存信息")
        expect("/media/1") {it.optString("title")=="本地编辑验证"}
        tap("返回");tap("下一页")
        assertNotNull(element("QA media 25"))
    }
    @Test fun userCreationAndEditing() {
        tap("用户管理");tap("添加用户")
        field("用户名（3–40 字符）","qa_native");field("密码（10–128 字符）","qa-only-password")
        tap("保存");expect("/users") {it.optString("username")=="qa_native"}
        assertNotNull(element("qa_native"))
    }
    @Test fun folderAndProxyConfiguration() {
        tap("服务器设置");tap("添加 / 更新目录")
        field("服务器绝对路径","/qa/native");tap("保存并扫描");tap("确认")
        expect("/folders") {it.optString("path")=="/qa/native"}
        field("代理 URL（留空关闭）","http://qa-proxy.invalid:8080");tap("保存代理设置")
        expect("/external/proxy") {it.optString("proxy")=="http://qa-proxy.invalid:8080"}
    }
    @Test fun recommendationUsesNativeResult() {
        tap("漫画推荐");field("偏好、题材、画风或剧情","QA preference");tap("生成推荐")
        expect("/recommend/manga") {it.optString("query")=="QA preference"}
        assertNotNull(element("QA preference matched"))
    }
    @Test fun wnacgSelectionDownloadAndAutoSync() {
        tap("外部收藏与下载");tap("WNACG")
        tap("开启自动同步");expect("/auto-sync/wnacg/1") {it.optBoolean("auto_sync_enabled")}
        tap("选择本页未入库作品");tap("下载选中作品");tap("确认")
        expect("/external/wnacg/downloads") {it.optJSONArray("item_ids")!!.length()>0&&it.optString("download_root_path")=="/qa/downloads"}
    }
    @Test fun pawchiveNativeImagesAndDownloadPreview() {
        tap("Pawchive");tap("浏览帖子");tap("打开附件");tap("全屏查看")
        assertNotNull(element("1 / 2"));tap("下一张");assertNotNull(element("2 / 2"));tap("关闭")
        tap("下载整篇帖子");tap("确认")
        expect("/external/pawchive/downloads/preview");expect("/external/pawchive/downloads")
    }
    @Test fun dedupConfirmationCancelDoesNotMutate() {
        val before=events().length()
        tap("重复管理");tap("保留原记录");tap("取消")
        assertEquals(before,events().length())
        tap("两者不是重复");tap("确认")
        expect("/dedup/candidates/1/resolve") {it.optString("action")=="keep_both"}
    }
    @Test fun largeTextNativeEditorRemainsUsable() {
        val density=device.executeShellCommand("wm density").substringAfter("Override density: ","").trim().toIntOrNull()
        val font=device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("wm density 460")
            device.executeShellCommand("settings put system font_scale 2.0")
            android.os.SystemClock.sleep(1500)
            tap("用户管理");tap("添加用户")
            assertNotNull(element("用户名（3–40 字符）"))
            assertNotNull(element("密码（10–128 字符）"))
            tap("取消")
            tap("返回");tap("统计看板")
            assertNotNull(element("媒体总数"));assertNotNull(element("总容量"))
        } finally {
            device.executeShellCommand(if(density==null) "wm density reset" else "wm density $density")
            device.executeShellCommand(if(font=="null") "settings delete system font_scale" else "settings put system font_scale $font")
        }
    }
}
