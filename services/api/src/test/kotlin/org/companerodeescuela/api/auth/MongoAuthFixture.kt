package org.companerodeescuela.api.auth

import com.mongodb.MongoClientSettings
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.reactivestreams.client.FindPublisher
import com.mongodb.reactivestreams.client.MongoCollection
import java.lang.reflect.Proxy
import org.bson.BsonDocument
import org.bson.BsonValue
import org.bson.Document
import org.bson.conversions.Bson
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

/** Deterministic reactive-driver boundary; this is not a real Mongo integration test. */
internal class MongoAuthFixture(var document: Document) {
    var beforeNextUpdate: (() -> Unit)? = null
    var beforeNextFindOneAndUpdate: (() -> Unit)? = null

    private val registry = MongoClientSettings.getDefaultCodecRegistry()
    private val collection = proxy<MongoCollection<Document>>(MongoCollection::class.java) { name, args ->
        when (name) {
            "getDocumentClass" -> Document::class.java
            "getCodecRegistry" -> registry
            "createIndex" -> publisher { "auth-test-index" }
            "find" -> {
                val filter = args.first() as Bson
                val snapshot = document.takeIf { matches(filter, it) }?.let(::Document)
                proxy<FindPublisher<Document>>(FindPublisher::class.java) { method, parameters ->
                    when (method) {
                        "subscribe" -> {
                            @Suppress("UNCHECKED_CAST")
                            publisher { snapshot }.subscribe(parameters.first() as Subscriber<in Document>)
                            null
                        }
                        else -> error("Unsupported FindPublisher operation: $method")
                    }
                }
            }
            "findOneAndUpdate" -> publisher {
                beforeNextFindOneAndUpdate?.also { beforeNextFindOneAndUpdate = null }?.invoke()
                val matched = matches(args[0] as Bson, document)
                if (!matched) null else {
                    applyUpdate(args[1] as Bson)
                    Document(document)
                }
            }
            "updateOne" -> publisher {
                beforeNextUpdate?.also { beforeNextUpdate = null }?.invoke()
                val matched = matches(args[0] as Bson, document)
                if (matched) applyUpdate(args[1] as Bson)
                UpdateResult.acknowledged(if (matched) 1 else 0, if (matched) 1 else 0, null)
            }
            else -> error("Unsupported collection operation: $name")
        }
    }

    val database = MongoDatabase(
        proxy<com.mongodb.reactivestreams.client.MongoDatabase>(com.mongodb.reactivestreams.client.MongoDatabase::class.java) { name, _ ->
            when (name) {
                "getCollection" -> collection
                else -> error("Unsupported database operation: $name")
            }
        },
    )

    private fun matches(filter: Bson, candidate: Document): Boolean =
        matchesDocument(filter.toBsonDocument(Document::class.java, registry), candidate.toBsonDocument(Document::class.java, registry))

    private fun matchesDocument(filter: BsonDocument, candidate: BsonDocument): Boolean = filter.all { (key, expected) ->
        when (key) {
            "$" + "and" -> expected.asArray().all { matchesDocument(it.asDocument(), candidate) }
            "$" + "or" -> expected.asArray().any { matchesDocument(it.asDocument(), candidate) }
            else -> {
                val actual = candidate[key]
                if (expected.isDocument) expected.asDocument().all { (operator, value) ->
                    when (operator) {
                        "$" + "gt" -> actual?.isDateTime == true && actual.asDateTime().value > value.asDateTime().value
                        "$" + "ne" -> !equal(actual, value)
                        "$" + "exists" -> (actual != null) == value.asBoolean().value
                        else -> error("Unsupported filter operator: $operator")
                    }
                } else equal(actual, expected)
            }
        }
    }

    private fun equal(actual: BsonValue?, expected: BsonValue): Boolean =
        if (actual?.isArray == true) actual.asArray().contains(expected)
        else actual == expected || (actual == null && expected.isNull)

    private fun applyUpdate(update: Bson) {
        val operations = Document.parse(update.toBsonDocument(Document::class.java, registry).toJson())
        for ((operator, values) in operations) {
            val fields = values as Document
            for ((key, value) in fields) when (operator) {
                "$" + "set" -> document[key] = value
                "$" + "inc" -> document[key] = (document[key] as? Number)?.toLong()?.plus((value as Number).toLong()) ?: value
                "$" + "push" -> document[key] = (document.getList(key, Document::class.java).orEmpty() + value)
                "$" + "addToSet" -> document[key] = (document.getList(key, String::class.java).orEmpty() + value.toString()).distinct()
                else -> error("Unsupported update operator: $operator")
            }
        }
    }

    private fun <T : Any> publisher(value: () -> T?): Publisher<T> = Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
            private var completed = false
            override fun request(n: Long) {
                if (completed || n <= 0) return
                completed = true
                try {
                    value()?.let(subscriber::onNext)
                    subscriber.onComplete()
                } catch (error: Exception) {
                    subscriber.onError(error)
                }
            }
            override fun cancel() { completed = true }
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<*>, invoke: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> invoke(method.name, args.orEmpty()) } as T
}
