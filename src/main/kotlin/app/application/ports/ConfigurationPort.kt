package app.application.ports

import app.domain.entities.LocalRepo
import app.domain.entities.User

/**
 * Port for configuration management.
 * This is an output port — abstracts reading/writing of application
 * configuration (credentials, tracked repos, user info).
 */
interface ConfigurationPort {
    fun getUsername(): String
    fun getPassword(): String
    fun isValidCredentials(): Boolean
    fun getLocalRepos(): List<LocalRepo>
    fun getUser(): User
    fun setUsernameCurrent(username: String)
    fun setPasswordCurrent(password: String)
    fun getUuidPersistent(): String
    fun setUsernamePersistent(username: String)
    fun setPasswordPersistent(password: String)
    fun addLocalRepoPersistent(localRepo: LocalRepo)
    fun removeLocalRepoPersistent(localRepo: LocalRepo)
    fun setUser(user: User)
    fun isFirstLaunch(): Boolean
    fun loadFromFile()
    fun saveToFile()
    fun resetAndSave()
}
