package de.mkammerer.grpcchat.server

import com.google.common.cache.CacheBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The user account.
 */
data class User(val username: String, val password: String)

/**
 * The user token.
 */
data class Token(val data: String) {
    override fun toString() = data
}

/**
 * The user service which provide action for
 */
interface UserService {
    /**
     * Register account with `username` and `password`
     *
     * @return the user account if successfully register, otherwise throw [UserAlreadyExistsException]
     */
    fun register(username: String, password: String): User

    /**
     * Login account with `username` and `password`
     *
     * @return the valid token for the user if login successfully, otherwise return null
     */
    fun login(username: String, password: String): Token?

    /**
     * Validate whether the `token` is within the valid period
     *
     * @return the user account if `token` still valid.
     */
    fun validateToken(token: Token): User?

    /**
     * Whether the user with `username` is exist in the memory.
     */
    fun exists(username: String): Boolean
}

/**
 * The user has already exist in the user account memory map, so you can't register it again or other similar operation.
 */
class UserAlreadyExistsException(username: String) : Exception("User '$username' already exists")

class InMemoryUserService(
        private val tokenGenerator: TokenGenerator
) : UserService {
    private val users = ConcurrentHashMap<String, User>()
    // the token for user just can live in 10 minute.
    private val loggedIn = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build<Token, User>()


    override fun exists(username: String): Boolean {
        return users.containsKey(username)
    }

    override fun register(username: String, password: String): User {
        // step 1. whether it has already registered.
        if (exists(username)) throw UserAlreadyExistsException(username)

        // step 2. create a user.
        val user = User(username, password)
        // step 3. put to the account memory map.
        users.put(user.username, user)
        return user
    }

    override fun login(username: String, password: String): Token? {
        // step 1. whether the user is existed in the account memory map.
        val user = users[username] ?: return null

        // step 2. whether the provided password is corrected.
        if (user.password == password) {
            // step 3. create token for the user
            val token = Token(tokenGenerator.create())
            // step 4. put into the logged in memory.
            loggedIn.put(token, user)
            return token
        } else return null
    }

    override fun validateToken(token: Token): User? {
        return loggedIn.getIfPresent(token)
    }
}