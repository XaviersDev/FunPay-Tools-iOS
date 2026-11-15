/*
 *
 *  * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *  *
 *  * This code is proprietary. Modification, distribution, or use
 *  * of this file without express written permission is strictly prohibited.
 *  * Unauthorized use will be prosecuted.
 *
 */

package ru.allisighs.funpaytools

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO


/** Hand-written replacement of the Retrofit interface (same method signatures). */
class FunPayApi(private val client: OkHttpClient) {
    private val base = "https://funpay.com/"
    private val formType = "application/x-www-form-urlencoded".toMediaType()

    private suspend fun exec(req: Request): Response = withContext(Dispatchers.IO) { client.newCall(req).execute() }

    private fun abs(path: String): String = if (path.startsWith("http")) path else base + path.trimStart('/')

    private fun get(url: String, headers: Map<String, String>): Request {
        val b = Request.Builder().url(abs(url)).get()
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    private fun postForm(url: String, headers: Map<String, String>, fields: List<Pair<String, String>>): Request {
        val fb = FormBody.Builder()
        fields.forEach { fb.add(it.first, it.second) }
        val b = Request.Builder().url(abs(url)).post(fb.build())
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    suspend fun getMainPage(cookie: String, userAgent: String): Response =
        exec(get("/", mapOf("Cookie" to cookie, "User-Agent" to userAgent)))

    suspend fun getChatHistory(url: String, cookie: String, userAgent: String, xRequestedWith: String): Response =
        exec(get(url, mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xRequestedWith)))

    suspend fun getChatPage(url: String, cookie: String, userAgent: String): Response =
        exec(get(url, mapOf("Cookie" to cookie, "User-Agent" to userAgent)))

    suspend fun getUserProfile(userId: String, cookie: String, userAgent: String): Response =
        exec(get("users/$userId/", mapOf("Cookie" to cookie, "User-Agent" to userAgent)))

    suspend fun runnerGet(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", objects: String, request: String, csrfToken: String): Response =
        exec(postForm("runner/", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq),
            listOf("objects" to objects, "request" to request, "csrf_token" to csrfToken)))

    suspend fun runnerSend(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", objects: String = "[]", request: String, csrfToken: String): Response =
        exec(postForm("runner/", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq),
            listOf("objects" to objects, "request" to request, "csrf_token" to csrfToken)))

    suspend fun raiseLotInitial(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", gameId: Int, nodeId: Int): Response =
        exec(postForm("lots/raise", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq),
            listOf("game_id" to gameId.toString(), "node_id" to nodeId.toString())))

    suspend fun raiseLotCommit(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", gameId: Int, nodeId: Int, nodeIds: List<Int>): Response =
        exec(postForm("lots/raise", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq),
            listOf("game_id" to gameId.toString(), "node_id" to nodeId.toString()) + nodeIds.map { "node_ids[]" to it.toString() }))

    suspend fun getConfirmationPage(cookie: String, userAgent: String): Response =
        exec(get("orders/new", mapOf("Cookie" to cookie, "User-Agent" to userAgent)))

    suspend fun getOrder(orderId: String, cookie: String, userAgent: String): Response =
        exec(get("orders/$orderId/", mapOf("Cookie" to cookie, "User-Agent" to userAgent)))

    suspend fun refundOrder(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", csrfToken: String, orderId: String): Response =
        exec(postForm("orders/refund", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq),
            listOf("csrf_token" to csrfToken, "id" to orderId)))

    suspend fun replyToReview(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", csrfToken: String, orderId: String, text: String, rating: Int = 5, authorId: String): Response =
        exec(postForm("orders/review", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq),
            listOf("csrf_token" to csrfToken, "orderId" to orderId, "text" to text, "rating" to rating.toString(), "authorId" to authorId)))

    suspend fun deleteReviewReply(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", csrfToken: String, orderId: String, authorId: String): Response =
        exec(postForm("orders/reviewDelete", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq),
            listOf("csrf_token" to csrfToken, "orderId" to orderId, "authorId" to authorId)))

    suspend fun uploadChatImage(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", file: MultipartBody.Part, fileId: RequestBody): Response {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addPart(file)
            .addPart(MultipartBody.Part.createFormData("file_id", null, fileId))
            .build()
        val req = Request.Builder().url(abs("file/addChatImage")).post(body)
            .header("Cookie", cookie).header("User-Agent", userAgent).header("X-Requested-With", xReq).build()
        return exec(req)
    }

    suspend fun rewriteText(url: String = "https://fptools.onrender.com/api/ai", contentType: String = "application/json", auth: String, body: RequestBody): Response {
        val req = Request.Builder().url(url).post(body).header("Content-Type", contentType).header("Authorization", auth).build()
        return exec(req)
    }

    suspend fun getLotPage(offerId: String, cookie: String, userAgent: String): Response =
        exec(get("lots/offer?id=" + urlEncodeForm(offerId), mapOf("Cookie" to cookie, "User-Agent" to userAgent)))

    suspend fun postOrderNew(cookie: String, userAgent: String, xReq: String = "XMLHttpRequest", accept: String = "application/json, text/javascript, */*; q=0.01", referer: String, fields: Map<String, String>): Response =
        exec(postForm("orders/new", mapOf("Cookie" to cookie, "User-Agent" to userAgent, "X-Requested-With" to xReq, "Accept" to accept, "Referer" to referer),
            fields.entries.map { it.key to it.value }))
}



object ApiFactory {
    private var authenticatorSet = false

    fun createClient(account: Account?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }
            // Актуальные куки (PHPSESSID/golden_seal) для каждого запроса + повтор при 428
            .addInterceptor(FpSessionInterceptor())
            // Set-Cookie с каждого ответа, включая редиректы
            .addNetworkInterceptor(FpCookieCaptureInterceptor())

        val pHost = account?.proxyHost ?: ""
        val pPort = account?.proxyPort ?: 0
        if (account != null && pHost.isNotBlank() && pPort > 0) {
            try {
                val proxyType = if (account.proxyType == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
                val proxyAddress = InetSocketAddress(pHost, pPort)
                builder.proxy(Proxy(proxyType, proxyAddress))

                val pUser = account.proxyUsername ?: ""
                val pPass = account.proxyPassword ?: ""
                if (pUser.isNotBlank()) {
                    registerProxyCredentials(pHost, pPort, pUser, pPass)
                    if (proxyType != Proxy.Type.SOCKS) {
                        builder.proxyAuthenticator { _, response ->
                            val credential = Credentials.basic(pUser, pPass)
                            response.request.newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                    }
                }
                Log.d("FPC_DBG", "Установлен прокси ${account.proxyType} для ${account.username}")
            } catch (e: Exception) {
                Log.e("FPC_LOG", "Ошибка прокси для ${account.username}: ${e.message}")
            }
        }
        return builder.build()
    }

    /** Общий клиент без прокси — для мест, где раньше создавался голый OkHttpClient(). */
    val shared: OkHttpClient by lazy { createClient(null) }

    fun create(account: Account? = null): FunPayApi {
        return FunPayApi(createClient(account))
    }
}