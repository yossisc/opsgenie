package com.glassbox.rotaportal.shared

interface TokenStore {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()

    fun hasToken(): Boolean = getToken() != null
}

