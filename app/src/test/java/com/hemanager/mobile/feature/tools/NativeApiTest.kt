package com.hemanager.mobile.feature.tools

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class NativeApiTest {
    @Test fun nativeTransportPreservesProtocolAndDoesNotExportCredentials() = runBlocking {
        val server=MockWebServer()
        val calls=java.util.concurrent.CopyOnWriteArrayList<Triple<String,String,String>>()
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path=request.requestUrl!!.encodedPath
                calls.add(Triple(request.method.orEmpty(),request.path.orEmpty(),request.body.readUtf8()))
                assertEquals("Bearer test-credential",request.getHeader("Authorization"))
                assertFalse(request.path.orEmpty().contains("test-credential"))
                return when(path) {
                    "/media" -> MockResponse().addHeader("X-Total-Count",103).setBody("[{\"id\":1,\"title\":\"作品\"}]")
                    "/empty" -> MockResponse().setResponseCode(204)
                    "/invalid" -> MockResponse().setResponseCode(422).setBody("{\"detail\":\"标题不能为空\"}")
                    "/external/pawchive/account/favorites" -> MockResponse().setResponseCode(401).setBody("{\"detail\":{\"code\":\"LOGIN_REQUIRED\",\"message\":\"请先登录 Pawchive\"}}")
                    "/redirect" -> MockResponse().setResponseCode(302).addHeader("Location","/forbidden")
                    else -> MockResponse().setBody("{\"saved\":true}")
                }
            }
        }
        server.start()
        try {
            val api=NativeApi(server.url("/").toString().trimEnd('/'),"test-credential")
            val page=api.request("/media?limit=24&offset=24")
            assertEquals(103,page.total);assertEquals("作品",(page.value as JSONArray).getJSONObject(0).getString("title"))
            assertEquals(0,api.request("/empty","DELETE").value.let {it as org.json.JSONObject}.length())
            api.send("/media/1",obj("title" to "原生修改","rating" to 5),"PATCH")
            assertTrue(calls.last().third.contains("原生修改"));assertEquals("PATCH",calls.last().first)
            try {api.get("/invalid");fail("expected validation error")} catch(e: NativeApiException) {assertEquals(422,e.status);assertEquals("标题不能为空",e.message)}
            try {api.get("/external/pawchive/account/favorites");fail("expected Pawchive login error")} catch(e: NativeApiException) {assertFalse(e.sessionExpired)}
            try {api.get("/redirect");fail("redirect must not be followed")} catch(e: NativeApiException) {assertEquals(302,e.status)}
            assertFalse(calls.any {it.second=="/forbidden"})
            api.upload("/archive","归档.zip") {"native-upload".byteInputStream()}
            assertTrue(calls.last().third.contains("name=\"file\""));assertTrue(calls.last().third.contains("归档.zip"));assertTrue(calls.last().third.contains("native-upload"))
        } finally {server.shutdown()}
    }
}
