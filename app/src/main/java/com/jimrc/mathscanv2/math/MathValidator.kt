package com.jimrc.mathscanv2.math

import net.objecthunter.exp4j.ExpressionBuilder

object MathValidator {
    fun validateExercise(expression: String): Boolean {
        // Asumimos que no hay '=' en la imagen, solo evaluamos
        return try {
            val exp = ExpressionBuilder(expression).build()
            exp.evaluate() // Si no lanza excepción, es válida
            true // O podrías devolver el resultado
        } catch (e: Exception) {
            false
        }
    }
    // Si necesitas validar "A+B=C"
    fun validateEquation(fullEquation: String): Boolean {
        if (!fullEquation.contains("=")) return false
        val parts = fullEquation.split("=")
        if (parts.size != 2) return false

        val (equation, userAnswerStr) = parts
        val userAnswer = userAnswerStr.toDoubleOrNull() ?: return false

        return try {
            val correctAnswer = ExpressionBuilder(equation).build().evaluate()
            correctAnswer == userAnswer
        } catch (e: Exception) {
            false
        }
    }
}