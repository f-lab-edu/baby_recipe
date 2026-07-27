import { createContext, useContext, useState, useCallback } from 'react'
import api from '../api/axios'
import { getItem, setItem, setPersistent, clearAuth } from '../api/tokenStorage'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const saved = getItem('user')
    return saved ? JSON.parse(saved) : null
  })

  const login = useCallback(async (email, password, persistent = false) => {
    const res = await api.post('/auth/login', { email, password })
    const { accessToken, refreshToken } = res.data.data
    setPersistent(persistent)
    setItem('accessToken', accessToken)
    setItem('refreshToken', refreshToken)

    const payload = JSON.parse(atob(accessToken.split('.')[1]))
    const userId = payload.sub
    const userRes = await api.get(`/users/${userId}`).catch(() => null)
    const userData = userRes?.data?.data || { id: userId, email }
    setItem('user', JSON.stringify(userData))
    setUser(userData)
    return userData
  }, [])

  const logout = useCallback(async () => {
    try { await api.post('/auth/logout') } catch {}
    clearAuth()
    setUser(null)
  }, [])

  const updateUser = useCallback((data) => {
    const updated = { ...user, ...data }
    setItem('user', JSON.stringify(updated))
    setUser(updated)
  }, [user])

  return (
    <AuthContext.Provider value={{ user, login, logout, updateUser, isLoggedIn: !!user }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
