package com.hemanager.mobile.ui.host

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.recyclerview.widget.RecyclerView

interface HostUiController {
    fun setCreatorsScreenActive(active: Boolean)

    fun setEdgeDrawerOpenRequester(requester: (() -> Unit)?)

    fun setEdgeDrawerGestureEnabled(enabled: Boolean)

    fun setImageGalleryPinchTouchListener(
        recyclerView: RecyclerView,
        enabled: Boolean,
        onPinchStart: (Offset) -> Unit,
        onPinch: (Float, Offset) -> Unit,
        onPinchEnd: () -> Unit,
    )
}

private object NoOpHostUiController : HostUiController {
    override fun setCreatorsScreenActive(active: Boolean) = Unit

    override fun setEdgeDrawerOpenRequester(requester: (() -> Unit)?) = Unit

    override fun setEdgeDrawerGestureEnabled(enabled: Boolean) = Unit

    override fun setImageGalleryPinchTouchListener(
        recyclerView: RecyclerView,
        enabled: Boolean,
        onPinchStart: (Offset) -> Unit,
        onPinch: (Float, Offset) -> Unit,
        onPinchEnd: () -> Unit,
    ) = Unit
}

val LocalHostUiController = staticCompositionLocalOf<HostUiController> {
    NoOpHostUiController
}
