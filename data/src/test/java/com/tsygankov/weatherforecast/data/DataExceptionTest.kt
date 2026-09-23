package com.tsygankov.weatherforecast.data

import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DataExceptionTest {
    @Test
    fun `network timeout HTTP parsing and empty data remain distinguishable`() {
        assertTrue(UnknownHostException().toDataException() is DataException.Network)
        assertTrue(IOException().toDataException() is DataException.Network)
        assertTrue(SocketTimeoutException().toDataException() is DataException.Timeout)
        assertTrue(JsonSyntaxException("bad JSON").toDataException() is DataException.Parsing)

        val httpError = HttpException(
            Response.error<Any>(
                503,
                "unavailable".toResponseBody("text/plain".toMediaType()),
            ),
        ).toDataException()
        assertTrue(httpError is DataException.Http)
        assertEquals(503, (httpError as DataException.Http).statusCode)

        val empty = DataException.EmptyData("hourly")
        assertSame(empty, empty.toDataException())
    }
}
