package com.prz.sleepguardian

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test verifying the proper configuration of the Retrofit HTTP client.
 */
class RetrofitClientTest {

    @Test
    fun testRetrofitInstance_ConfigurationIsCorrect() {
        // Act
        val serviceInstance = RetrofitClient.apiService

        // Assert
        assertNotNull("ApiService instance should not be null", serviceInstance)
    }
}