package com.example

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLNonTransientConnectionException
import java.util.Properties

internal fun connectToDatabase(): Connection {
    val url = System.getenv("COOKBOOK_DB_URL") ?: "jdbc:postgresql://localhost:5432/cookbook_db"
    val user = System.getenv("COOKBOOK_DB_USER") ?: "postgres"
    val password = System.getenv("COOKBOOK_DB_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: throw SQLNonTransientConnectionException("COOKBOOK_DB_PASSWORD is required", "08001")
    return DriverManager.getConnection(url, Properties().apply {
        setProperty("user", user)
        setProperty("password", password)
        setProperty("connectTimeout", "8")
        setProperty("socketTimeout", "12")
    })
}
