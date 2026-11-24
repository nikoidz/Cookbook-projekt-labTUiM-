package com.example


import java.sql.Connection
import java.sql.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecipeInput(
    val title: String? = null,
    val preparationTime: String? = null,
    val ingredients: List<String>? = null,
    val steps: List<String>? = null
)

internal class RecipeStore(private val connect: () -> Connection) {

    // Zamiana wiersza z PostgreSQL na obiekt Recipe
    private fun readRecipe(result: ResultSet): Recipe {

        val ingredients =
            (result.getArray("ingredients").array as Array<*>)
                .map { it.toString() }

        val steps =
            (result.getArray("steps").array as Array<*>)
                .map { it.toString() }

        return Recipe(
            id = result.getInt("id"),
            title = result.getString("title"),
            preparationTime = result.getString("preparation_time"),
            ingredients = ingredients,
            steps = steps
        )
    }

    // GET - wszystkie publiczne przepisy
    suspend fun getAll(): List<Recipe> = withContext(Dispatchers.IO) {

        connect().use { connection ->

            val sql = """
                SELECT id, title, preparation_time, ingredients, steps
                FROM recipes
                WHERE is_public = TRUE
                ORDER BY id
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->

                statement.executeQuery().use { result ->

                    val recipes = mutableListOf<Recipe>()

                    while (result.next()) {
                        recipes.add(readRecipe(result))
                    }

                    recipes
                }
            }
        }
    }

    // GET - pojedynczy przepis
    suspend fun getById(id: Int): Recipe? =
        withContext(Dispatchers.IO) {

            connect().use { connection ->

                val sql = """
                    SELECT id, title, preparation_time, ingredients, steps
                    FROM recipes
                    WHERE id = ? AND is_public = TRUE
                """.trimIndent()

                connection.prepareStatement(sql).use { statement ->

                    statement.setInt(1, id)

                    statement.executeQuery().use { result ->

                        if (result.next()) {
                            readRecipe(result)
                        } else {
                            null
                        }
                    }
                }
            }
        }


    // GET - przepisy zalogowanego użytkownika
    suspend fun getMine(userId: Int): List<Recipe> =
        withContext(Dispatchers.IO) {

            connect().use { connection ->

                val sql = """
                    SELECT id, title, preparation_time, ingredients, steps
                    FROM recipes
                    WHERE author_id = ?
                    ORDER BY id
                """.trimIndent()

                connection.prepareStatement(sql).use { statement ->

                    statement.setInt(1, userId)

                    statement.executeQuery().use { result ->

                        val recipes = mutableListOf<Recipe>()

                        while (result.next()) {
                            recipes.add(readRecipe(result))
                        }

                        recipes
                    }
                }
            }
        }

    // Pomocnicze przypisywanie danych przepisu do SQL
    private fun setRecipeValues(
        statement: java.sql.PreparedStatement,
        connection: Connection,
        input: RecipeInput,
        startIndex: Int
    ) {

        statement.setString(
            startIndex,
            input.title!!.trim()
        )

        statement.setString(
            startIndex + 1,
            input.preparationTime!!.trim()
        )

        statement.setArray(
            startIndex + 2,
            connection.createArrayOf(
                "text",
                input.ingredients!!.toTypedArray()
            )
        )

        statement.setArray(
            startIndex + 3,
            connection.createArrayOf(
                "text",
                input.steps!!.toTypedArray()
            )
        )
    }

    // POST - dodawanie przepisu z przypisaniem autora
    suspend fun add(
        input: RecipeInput,
        userId: Int
    ): Recipe = withContext(Dispatchers.IO) {

        connect().use { connection ->

            val sql = """
                INSERT INTO recipes
                    (author_id, title, preparation_time, ingredients, steps)
                VALUES (?, ?, ?, ?, ?)
                RETURNING
                    id, title, preparation_time, ingredients, steps
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->

                statement.setInt(1, userId)

                setRecipeValues(
                    statement,
                    connection,
                    input,
                    2
                )

                statement.executeQuery().use { result ->

                    check(result.next()) {
                        "Nie udalo sie dodac przepisu"
                    }

                    readRecipe(result)
                }
            }
        }
    }

    // PUT - edytowanie tylko własnego przepisu
    suspend fun update(
        id: Int,
        input: RecipeInput,
        userId: Int
    ): Recipe? = withContext(Dispatchers.IO) {

        connect().use { connection ->

            val sql = """
                UPDATE recipes
                SET title = ?,
                    preparation_time = ?,
                    ingredients = ?,
                    steps = ?
                WHERE id = ?
                  AND author_id = ?
                RETURNING
                    id, title, preparation_time, ingredients, steps
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->

                setRecipeValues(
                    statement,
                    connection,
                    input,
                    1
                )

                statement.setInt(5, id)
                statement.setInt(6, userId)

                statement.executeQuery().use { result ->

                    if (result.next()) {
                        readRecipe(result)
                    } else {
                        null
                    }
                }
            }
        }
    }

    // DELETE - usuwanie tylko własnego przepisu
    suspend fun delete(
        id: Int,
        userId: Int
    ): Boolean = withContext(Dispatchers.IO) {

        connect().use { connection ->

            val sql = """
                DELETE FROM recipes
                WHERE id = ?
                  AND author_id = ?
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->

                statement.setInt(1, id)
                statement.setInt(2, userId)

                statement.executeUpdate() > 0
            }
        }
    }
}
