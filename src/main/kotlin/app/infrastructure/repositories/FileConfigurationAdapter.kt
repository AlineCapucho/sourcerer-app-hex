package app.infrastructure.repositories

import app.application.ports.ConfigurationPort
import app.domain.entities.LocalRepo
import app.domain.entities.User
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.util.UUID

/**
 * Adapter: Implements ConfigurationPort using YAML file persistence.
 * This is an output adapter — reads/writes configuration from the filesystem.
 */
class FileConfigurationAdapter : ConfigurationPort {

    private val CONFIG_FILE_NAME = "config.yaml"

    private var current: ConfigData = ConfigData()
    private var persistent: ConfigData = ConfigData()
    private val default: ConfigData = ConfigData()

    private val config: ConfigData
        get() = default.merge(persistent).merge(current)

    private var user: User = User()

    private val mapper = ObjectMapper(YAMLFactory())
        .setVisibility(PropertyAccessor.ALL, Visibility.NONE)
        .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        .registerModule(KotlinModule())

    init {
        loadFromFile()
        assignUuidIfMissing()
    }

    private fun assignUuidIfMissing() {
        if (persistent.uuid.isNotEmpty()) return
        persistent.uuid = UUID.randomUUID().toString()
    }

    override fun getUsername(): String = config.username

    override fun getPassword(): String = config.password

    override fun isValidCredentials(): Boolean {
        return config.username.isNotEmpty() && config.password.isNotEmpty()
    }

    override fun getLocalRepos(): List<LocalRepo> = config.localRepos.toList()

    override fun getUser(): User = user

    override fun setUsernameCurrent(username: String) {
        current.username = username
    }

    override fun setPasswordCurrent(password: String) {
        current.password = hashPassword(password)
    }

    override fun getUuidPersistent(): String = persistent.uuid

    override fun setUsernamePersistent(username: String) {
        persistent.username = username
    }

    override fun setPasswordPersistent(password: String) {
        persistent.password = hashPassword(password)
    }

    override fun addLocalRepoPersistent(localRepo: LocalRepo) {
        persistent.localRepos.remove(localRepo)
        persistent.localRepos.add(localRepo)
    }

    override fun removeLocalRepoPersistent(localRepo: LocalRepo) {
        persistent.localRepos.remove(localRepo)
    }

    override fun setUser(user: User) {
        this.user = user
    }

    override fun isFirstLaunch(): Boolean {
        return persistent.password.isEmpty()
            && persistent.username.isEmpty()
            && persistent.localRepos.isEmpty()
    }

    override fun loadFromFile() {
        var loadConfig = ConfigData()
        try {
            val path = getConfigPath()
            if (Files.exists(path)) {
                loadConfig = Files.newBufferedReader(path).use {
                    mapper.readValue(it, ConfigData::class.java)
                }
            }
        } catch (e: NoSuchFileException) {
            // First launch, no config file yet
        } catch (e: IOException) {
            System.err.println("[e] Cannot access config file: ${e.message}")
        } catch (e: SecurityException) {
            System.err.println("[e] Cannot access config file: ${e.message}")
        } catch (e: InvalidPathException) {
            System.err.println("[e] Cannot access config file: ${e.message}")
        } catch (e: JsonParseException) {
            System.err.println("[e] Cannot parse config file: ${e.message}")
        } catch (e: JsonMappingException) {
            System.err.println("[e] Cannot parse config file: ${e.message}")
        }
        persistent = loadConfig
    }

    override fun saveToFile() {
        try {
            val path = getConfigPath()
            val parent = path.parent
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent)
            }
            Files.newBufferedWriter(path).use {
                mapper.writeValue(it, persistent)
            }
        } catch (e: IOException) {
            System.err.println("[e] Cannot save config file: ${e.message}")
        } catch (e: SecurityException) {
            System.err.println("[e] Cannot save config file: ${e.message}")
        } catch (e: InvalidPathException) {
            System.err.println("[e] Cannot save config file: ${e.message}")
        }
    }

    override fun resetAndSave() {
        persistent = ConfigData()
        current = ConfigData()
        saveToFile()
    }

    private fun getConfigPath(): java.nio.file.Path {
        val jarPath = try {
            val fullPathURI = this::class.java.protectionDomain
                .codeSource.location.toURI()
            val fullPath = java.nio.file.Paths.get(fullPathURI)
            val root = fullPath.root
            root.resolve(fullPath.subpath(0, fullPath.nameCount - 1))
        } catch (e: Exception) {
            java.nio.file.Paths.get(".")
        }
        val dataPath = jarPath.resolve("data")
        if (Files.notExists(dataPath)) {
            Files.createDirectories(dataPath)
        }
        return dataPath.resolve(CONFIG_FILE_NAME)
    }

    private fun hashPassword(password: String): String {
        return if (password.isEmpty()) ""
        else org.apache.commons.codec.digest.DigestUtils.sha256Hex(password)
    }

    /**
     * Internal config data class for YAML serialization.
     */
    class ConfigData(
        var uuid: String = "",
        var username: String = "",
        var password: String = "",
        var localRepos: MutableSet<LocalRepo> = mutableSetOf()
    ) {
        fun merge(other: ConfigData): ConfigData {
            if (other.username.isNotEmpty()) username = other.username
            if (other.password.isNotEmpty()) password = other.password
            if (other.localRepos.isNotEmpty()) localRepos = other.localRepos
            return this
        }
    }
}
