package de.mkammerer.grpcchat.server

import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter


/**
 * Created by Jacksgong on 15/03/2017.
 */
class SLogger(tag: String) {

    private val logger: Logger = Logger.getLogger(tag)

    init {
        val logFile = File("logs/running.log")
        if (!logFile.exists()) {
            logFile.parentFile.mkdirs()
            logFile.createNewFile()
        }
        val fh = FileHandler(logFile.absolutePath)
        fh.formatter = SimpleFormatter()
        logger.addHandler(fh)
    }

    fun info(msg: String) {
        logger.info(msg)
    }

    companion object {
        fun create(clazz: Class<*>) = SLogger(clazz.name)
    }

}
