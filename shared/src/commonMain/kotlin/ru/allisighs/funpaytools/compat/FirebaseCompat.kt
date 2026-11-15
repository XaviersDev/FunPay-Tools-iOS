package ru.allisighs.funpaytools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/* Tiny Firebase Realtime Database client over the REST API (no SDK on iOS). */

const val FIREBASE_DB_URL = "https://funpay-tools-default-rtdb.firebaseio.com"

class FirebaseDatabase private constructor() {
    companion object {
        private val inst = FirebaseDatabase()
        fun getInstance(): FirebaseDatabase = inst
        fun getInstance(url: String): FirebaseDatabase = inst
    }
    fun setPersistenceEnabled(b: Boolean) {}
    fun getReference(): DatabaseReference = DatabaseReference("")
    fun getReference(path: String): DatabaseReference = DatabaseReference(path.trim('/'))
    fun goOnline() {}
    fun goOffline() {}
}

class DatabaseError(val message: String, val code: Int = -1) {
    fun toException() = Exception(message)
}

interface ValueEventListener {
    fun onDataChange(snapshot: DataSnapshot)
    fun onCancelled(error: DatabaseError)
}

class DataSnapshot(val key: String?, internal val raw: Any?) {
    fun exists(): Boolean = raw != null && raw != JSONObject.NULL
    val value: Any? get() = raw
    @kotlin.jvm.JvmName("getValueFn") fun getValue(): Any? = raw
    inline fun <reified T : Any> getValue(cls: KClass<T>): T? = try {
        if (!exists()) null else gsonDecode<T>(gsonJson, jsonText(), kotlin.reflect.typeOf<T>())
    } catch (e: Exception) { null }
    fun jsonText(): String = when (raw) {
        is JSONObject -> raw.toString()
        is JSONArray -> raw.toString()
        is String -> JSONObject.quote(raw)
        null -> "null"
        else -> raw.toString()
    }
    fun child(path: String): DataSnapshot {
        var cur: Any? = raw
        for (p in path.split('/').filter { it.isNotEmpty() }) cur = (cur as? JSONObject)?.opt(p)
        return DataSnapshot(path.substringAfterLast('/'), cur)
    }
    fun hasChild(path: String): Boolean = child(path).exists()
    val children: Iterable<DataSnapshot> get() = (raw as? JSONObject)?.let { o -> o.keys().asSequence().map { DataSnapshot(it, o.opt(it)) }.toList() } ?: emptyList()
    val childrenCount: Long get() = (raw as? JSONObject)?.length()?.toLong() ?: 0L
}

class FirebaseTask<T> {
    private var success: ((T) -> Unit)? = null
    private var failure: ((Exception) -> Unit)? = null
    private var complete: ((FirebaseTask<T>) -> Unit)? = null
    private var result: T? = null
    private var error: Exception? = null
    private var done = false
    val isSuccessful: Boolean get() = done && error == null
    val exception: Exception? get() = error
    fun getResult(): T? = result

    fun addOnSuccessListener(l: (T) -> Unit): FirebaseTask<T> { success = l; if (done && error == null) PlatformBridge.runOnMain { l(result as T) }; return this }
    fun addOnFailureListener(l: (Exception) -> Unit): FirebaseTask<T> { failure = l; error?.let { e -> PlatformBridge.runOnMain { l(e) } }; return this }
    fun addOnCompleteListener(l: (FirebaseTask<T>) -> Unit): FirebaseTask<T> { complete = l; if (done) PlatformBridge.runOnMain { l(this) }; return this }

    internal fun finish(r: T?, e: Exception?) {
        PlatformBridge.runOnMain {
            result = r; error = e; done = true
            if (e == null) success?.invoke(r as T) else failure?.invoke(e)
            complete?.invoke(this)
        }
    }
}

class DatabaseReference internal constructor(val path: String) {
    val key: String? get() = path.substringAfterLast('/').ifEmpty { null }
    fun child(p: String): DatabaseReference = DatabaseReference(listOf(path, p.trim('/')).filter { it.isNotEmpty() }.joinToString("/"))
    fun push(): DatabaseReference = child(UUID.randomUUID().toString().replace("-", "").take(20))
    private val url: String get() = "$FIREBASE_DB_URL/$path.json"

    private fun request(method: String, body: String?): String? {
        val b = Request.Builder().url(url)
        if (body != null) b.method(method, body.toRequestBody("application/json".toMediaType())) else b.method(method, null)
        val resp = ApiFactory.shared.newCall(b.build()).execute()
        val text = resp.body?.string()
        if (!resp.isSuccessful) throw IOException("Firebase HTTP ${resp.code}")
        return text
    }

    fun get(): FirebaseTask<DataSnapshot> {
        val t = FirebaseTask<DataSnapshot>()
        appScope.launch(Dispatchers.IO) {
            try {
                val text = request("GET", null) ?: "null"
                val v = if (text.trim() == "null") null else JsonTokener(text).nextValue()
                t.finish(DataSnapshot(key, v), null)
            } catch (e: Exception) { t.finish(null, e) }
        }
        return t
    }

    fun addListenerForSingleValueEvent(l: ValueEventListener) {
        get().addOnSuccessListener { l.onDataChange(it) }.addOnFailureListener { l.onCancelled(DatabaseError(it.message ?: "error")) }
    }

    fun addValueEventListener(l: ValueEventListener): ValueEventListener { addListenerForSingleValueEvent(l); return l }
    fun removeEventListener(l: ValueEventListener) {}

    fun setValue(v: Any?): FirebaseTask<Unit> = write("PUT", v)
    fun updateChildren(v: Map<String, Any?>): FirebaseTask<Unit> = write("PATCH", v)
    fun removeValue(): FirebaseTask<Unit> = write("DELETE", null)

    private fun write(method: String, v: Any?): FirebaseTask<Unit> {
        val t = FirebaseTask<Unit>()
        appScope.launch(Dispatchers.IO) {
            try {
                val body = if (method == "DELETE") null else gsonEncodeAny(gsonJson, v)
                request(method, body)
                t.finish(Unit, null)
            } catch (e: Exception) { t.finish(null, e) }
        }
        return t
    }

    fun onDisconnect(): OnDisconnect = OnDisconnect()
    fun keepSynced(b: Boolean) {}
    fun orderByChild(c: String) = this
    fun limitToLast(n: Int) = this
    fun limitToFirst(n: Int) = this
}

class OnDisconnect {
    fun removeValue() {}
    fun setValue(v: Any?) {}
    fun cancel() {}
}

object ServerValue {
    val TIMESTAMP: Map<String, String> = mapOf(".sv" to "timestamp")
}
