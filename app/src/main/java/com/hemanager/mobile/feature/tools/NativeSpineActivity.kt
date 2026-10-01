package com.hemanager.mobile.feature.tools

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import com.badlogic.gdx.*
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.FloatArray
import com.esotericsoftware.spine.*
import java.io.File

/** Fully native OpenGL renderer. All controls are Android widgets; no HTML/JS. */
class NativeSpineActivity : AndroidApplication() {
    private lateinit var renderer: SpineRenderer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dir=File(intent.getStringExtra("directory").orEmpty()).canonicalFile
        val allowed=File(cacheDir,"bd2").canonicalPath+File.separator
        if(!dir.path.startsWith(allowed)) {finish();return}
        val skeleton=intent.getStringExtra("skeleton").orEmpty();val atlas=intent.getStringExtra("atlas").orEmpty()
        if(skeleton!=File(skeleton).name||atlas!=File(atlas).name) {finish();return}
        renderer=SpineRenderer(dir,skeleton,atlas) {e->runOnUiThread {AlertDialog.Builder(this).setTitle("动画加载失败").setMessage(e).setPositiveButton("关闭") {_,_->finish()}.setOnCancelListener {finish()}.show()}}
        val config=AndroidApplicationConfiguration().apply {useAccelerometer=false;useCompass=false;useGyroscope=false;useImmersiveMode=false}
        val renderView=initializeForView(renderer,config)
        val layout=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff0e1016.toInt());fitsSystemWindows=true}
        val header=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        fun button(label: String,action: ()->Unit)=Button(this).apply {text=label;minHeight=(48*resources.displayMetrics.density).toInt();setOnClickListener {action()}}
        header.addView(button("返回") {finish()})
        header.addView(TextView(this).apply {text=intent.getStringExtra("title");setTextColor(0xffeeeeee.toInt());textSize=16f;setPadding(12,20,12,12)},LinearLayout.LayoutParams(0,-2,1f))
        layout.addView(header);layout.addView(renderView,LinearLayout.LayoutParams(-1,0,1f))
        val controls=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val row=LinearLayout(this)
        if(resources.configuration.fontScale>1.3f) row.orientation=LinearLayout.VERTICAL
        row.addView(button("动画") {renderer.chooseAnimations {names->runOnUiThread {AlertDialog.Builder(this).setTitle("选择动画").setItems(names.toTypedArray()) {_,i->renderer.animate(names[i])}.show()}}})
        row.addView(button("皮肤") {renderer.chooseSkins {names->runOnUiThread {AlertDialog.Builder(this).setTitle("选择皮肤").setItems(names.toTypedArray()) {_,i->renderer.skin(names[i])}.show()}}})
        row.addView(button("适应屏幕") {renderer.fit()});controls.addView(row)
        val zoom=LinearLayout(this)
        zoom.addView(button("放大") {renderer.zoom(0.8f)})
        zoom.addView(button("缩小") {renderer.zoom(1.25f)});controls.addView(zoom)
        val options=LinearLayout(this)
        if(resources.configuration.fontScale>1.3f) options.orientation=LinearLayout.VERTICAL
        options.addView(CheckBox(this).apply {text="暂停";setTextColor(-1);setOnCheckedChangeListener {_,v->renderer.paused=v}})
        options.addView(CheckBox(this).apply {text="循环";isChecked=true;setTextColor(-1);setOnCheckedChangeListener {_,v->renderer.loop=v}})
        options.addView(CheckBox(this).apply {text="隐藏特效";setTextColor(-1);setOnCheckedChangeListener {_,v->renderer.hideEffects=v}});controls.addView(options)
        val speed=TextView(this).apply {text="速度 1.0× · 双指缩放，单指平移";setTextColor(-1);setPadding(16,8,16,8)};controls.addView(speed)
        controls.addView(SeekBar(this).apply {max=195;progress=95;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {override fun onProgressChanged(s: SeekBar?,value: Int,fromUser: Boolean) {renderer.speed=(value+5)/100f;speed.text="速度 %.2f× · 双指缩放，单指平移".format(renderer.speed)};override fun onStartTrackingTouch(s: SeekBar?) {};override fun onStopTrackingTouch(s: SeekBar?) {}})})
        val scroll=ScrollView(this).apply {addView(controls);isFillViewport=true}
        val controlsHeight=minOf(230f,resources.configuration.screenHeightDp*0.35f)
        layout.addView(scroll,LinearLayout.LayoutParams(-1,(controlsHeight*resources.displayMetrics.density).toInt()));setContentView(layout)
    }

    private class SpineRenderer(val directory: File,val skeletonName: String,val atlasName: String,val error: (String)->Unit): ApplicationAdapter() {
        @Volatile var paused=false;@Volatile var loop=true;@Volatile var hideEffects=false;@Volatile var speed=1f
        private var atlas: TextureAtlas?=null;private var batch: PolygonSpriteBatch?=null;private var skeleton: Skeleton?=null;private var state: AnimationState?=null
        private val renderer=SkeletonRenderer().apply {setPremultipliedAlpha(true)};private val camera=OrthographicCamera()
        private val offset=Vector2();private val bounds=Vector2();private var animation="";private var lastLoop=true;private var pinchBase=1f
        private val effect=Regex("lighting|effect|glow|shine|spark|particle|flash|aura|blur|_light\\b|\\blight_",RegexOption.IGNORE_CASE)
        override fun create() {
            try {
                atlas=TextureAtlas(Gdx.files.absolute(File(directory,atlasName).path))
                val data=SkeletonBinary(atlas).readSkeletonData(Gdx.files.absolute(File(directory,skeletonName).path))
                skeleton=Skeleton(data);state=AnimationState(AnimationStateData(data));batch=PolygonSpriteBatch()
                animation=data.animations.firstOrNull()?.name.orEmpty();if(animation.isNotBlank()) state!!.setAnimation(0,animation,loop)
                state!!.apply(skeleton);skeleton!!.updateWorldTransform();fitNow()
                Gdx.input.inputProcessor=GestureDetector(object:GestureDetector.GestureAdapter() {
                    override fun pan(x: Float,y: Float,dx: Float,dy: Float): Boolean {camera.position.x-=dx*camera.viewportWidth/Gdx.graphics.width*camera.zoom;camera.position.y+=dy*camera.viewportHeight/Gdx.graphics.height*camera.zoom;return true}
                    override fun pinch(initial1: Vector2,initial2: Vector2,p1: Vector2,p2: Vector2): Boolean {camera.zoom=(pinchBase*initial1.dst(initial2)/p1.dst(p2).coerceAtLeast(1f)).coerceIn(0.1f,8f);return true}
                    override fun pinchStop() {pinchBase=camera.zoom}
                })
            } catch(e: Exception) {error(e.message ?: "素材格式不受支持")}
        }
        private fun fitNow() {
            val s=skeleton ?: return
            s.getBounds(offset,bounds,FloatArray());if(bounds.x<=0||bounds.y<=0) {bounds.set(1000f,1000f);offset.set(-500f,0f)}
            val ratio=Gdx.graphics.width.toFloat()/Gdx.graphics.height.coerceAtLeast(1)
            camera.viewportHeight=maxOf(bounds.y*1.15f,bounds.x*1.15f/ratio);camera.viewportWidth=camera.viewportHeight*ratio
            camera.position.set(offset.x+bounds.x/2,offset.y+bounds.y/2,0f);camera.zoom=1f;pinchBase=1f;camera.update()
        }
        override fun resize(width: Int,height: Int) {if(skeleton!=null) fitNow()}
        override fun render() {
            Gdx.gl.glClearColor(0.055f,0.063f,0.086f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            val s=skeleton ?: return;val b=batch ?: return;val a=state ?: return
            if(lastLoop!=loop&&animation.isNotBlank()) {a.setAnimation(0,animation,loop);lastLoop=loop}
            if(!paused) a.update(Gdx.graphics.deltaTime.coerceAtMost(0.1f)*speed)
            s.setSlotsToSetupPose();a.apply(s)
            if(hideEffects) s.slots.forEach {slot->if(effect.containsMatchIn(slot.data.name)) slot.attachment=null}
            s.updateWorldTransform();camera.update();b.projectionMatrix=camera.combined;b.begin();renderer.draw(b,s);b.end()
        }
        fun chooseAnimations(callback: (List<String>)->Unit) {Gdx.app.postRunnable {callback(skeleton?.data?.animations?.map {it.name}.orEmpty())}}
        fun chooseSkins(callback: (List<String>)->Unit) {Gdx.app.postRunnable {callback(skeleton?.data?.skins?.map {it.name}.orEmpty())}}
        fun animate(name: String) {Gdx.app.postRunnable {animation=name;state?.setAnimation(0,name,loop)}}
        fun skin(name: String) {Gdx.app.postRunnable {skeleton?.setSkin(name);skeleton?.setToSetupPose();skeleton?.updateWorldTransform();fitNow()}}
        fun zoom(factor: Float) {Gdx.app.postRunnable {camera.zoom=(camera.zoom*factor).coerceIn(0.1f,8f);pinchBase=camera.zoom}}
        fun fit() {Gdx.app.postRunnable {fitNow()}}
        override fun dispose() {batch?.dispose();atlas?.dispose()}
    }
}
