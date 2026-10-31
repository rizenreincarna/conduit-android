package app.conduit

import kotlinx.coroutines.CancellationException

interface LocalizedError { val errorDescription: String? }

val Throwable.localizedDescription: String
    get() = (this as? LocalizedError)?.errorDescription ?: message ?: toString()

inline fun <T> tryOrNull(body: () -> T): T? = try {
    body()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

interface Identifiable<ID> { val id: ID }
