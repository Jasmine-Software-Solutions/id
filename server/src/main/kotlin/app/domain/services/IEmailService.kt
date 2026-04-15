package app.domain.services

import java.io.File

interface IEmailService {
    fun send(to: String, subject: String, body: String, files: List<File> = listOf())
}