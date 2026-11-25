package com.tsygankov.weatherforecast.data

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

// Закрытая иерархия задаёт типы ошибок слоя данных для разбора через when.
sealed class DataException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) :
        DataException("Нет подключения к сети", cause)

    class Timeout(cause: Throwable) :
        DataException("Сервер не ответил вовремя", cause)

    class Http(
        val statusCode: Int,
        cause: Throwable,
    ) : DataException("Сервис погоды временно недоступен", cause)

    class Parsing(cause: Throwable) :
        DataException("Не удалось прочитать ответ сервиса погоды", cause)

    class EmptyData(val missingPart: String) :
        DataException("Сервис погоды не вернул необходимые данные")

    class Unexpected(cause: Throwable) :
        DataException("Не удалось получить данные о погоде", cause)
}

// Java-библиотеки могут оборачивать исходную ошибку. Kotlin-последовательность
// проходит по цепочке cause, чтобы отделить, например, SocketTimeoutException от IOException.
internal fun Throwable.toDataException(): DataException {
    val causes = generateSequence(this) { it.cause }.toList()
    causes.filterIsInstance<DataException>().firstOrNull()?.let { return it }
    causes.filterIsInstance<HttpException>().firstOrNull()?.let {
        return DataException.Http(it.code(), it)
    }
    causes.filterIsInstance<SocketTimeoutException>().firstOrNull()?.let {
        return DataException.Timeout(it)
    }
    causes.firstOrNull { it is JsonParseException || it is MalformedJsonException }?.let {
        return DataException.Parsing(it)
    }
    causes.filterIsInstance<IOException>().firstOrNull()?.let {
        return DataException.Network(it)
    }
    return DataException.Unexpected(this)
}
