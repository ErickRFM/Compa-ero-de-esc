package org.companerodeescuela.api.auth

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.mail.ResendVerificationEmailGateway
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

class VerificationEmailTransportTest {
    @Test fun `real HTTP adapter sends only to configured recipient with stable idempotency`()=runTest {
        val calls=AtomicInteger(); val bodies=mutableListOf<String>(); val keys=mutableListOf<String>()
        val server=server { exchange ->
            bodies.add(exchange.requestBody.bufferedReader().readText()); keys.add(exchange.requestHeaders.getFirst("Idempotency-Key"))
            val attempt=calls.incrementAndGet()
            reply(exchange,if(attempt==1) 503 else 200,if(attempt==1) "{}" else """{"id":"accepted-local-test"}""")
        }
        try {
            val result=gateway(server).send("recipient@example.test","opaque-test-token","stable-operation")
            assertEquals(VerificationDeliveryStatus.ACCEPTED,result)
            assertEquals(2,calls.get()); assertEquals(listOf("stable-operation","stable-operation"),keys)
            assertEquals(bodies[0],bodies[1]); assertTrue(bodies[0].contains("recipient@example.test")); assertTrue(bodies[0].contains("opaque-test-token"))
        } finally {server.stop(0)}
    }
    @Test fun `provider rejection does not pretend an email was accepted`()=runTest {
        val server=server {reply(it,400,"""{"message":"rejected"}""")}
        try {assertEquals(VerificationDeliveryStatus.FAILED,gateway(server).send("recipient@example.test","token","operation"))}
        finally {server.stop(0)}
    }
    @Test fun `unbounded or invalid success response is explicitly unconfirmed`()=runTest {
        val server=server {reply(it,200,"x".repeat(20000))}
        try {assertEquals(VerificationDeliveryStatus.UNCONFIRMED,gateway(server).send("recipient@example.test","token","operation"))}
        finally {server.stop(0)}
    }
    @Test fun `in-progress idempotency conflict remains unconfirmed`()=runTest {
        val server=server {reply(it,409,"""{"name":"concurrent_idempotent_requests"}""")}
        try {assertEquals(VerificationDeliveryStatus.UNCONFIRMED,gateway(server).send("recipient@example.test","token","operation"))}
        finally {server.stop(0)}
    }
    @Test fun `retry rejection cannot erase an earlier ambiguous outcome`()=runTest {
        val calls=AtomicInteger()
        val server=server { exchange ->
            if(calls.incrementAndGet()==1) reply(exchange,503,"{}") else reply(exchange,400,"""{"message":"rejected retry"}""")
        }
        try {assertEquals(VerificationDeliveryStatus.UNCONFIRMED,gateway(server).send("recipient@example.test","token","operation"))}
        finally {server.stop(0)}
    }
    private fun gateway(server:HttpServer)=ResendVerificationEmailGateway("test-key-for-loopback-only".toCharArray(),"sender@example.test",URI("http://127.0.0.1:${server.address.port}/emails"))
    private fun server(handler:(com.sun.net.httpserver.HttpExchange)->Unit)=HttpServer.create(InetSocketAddress("127.0.0.1",0),0).also{
        it.createContext("/emails"){exchange->try{handler(exchange)} finally{exchange.close()}};it.start()
    }
    private fun reply(exchange:com.sun.net.httpserver.HttpExchange,status:Int,body:String){
        val bytes=body.toByteArray();exchange.sendResponseHeaders(status,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}
    }
}