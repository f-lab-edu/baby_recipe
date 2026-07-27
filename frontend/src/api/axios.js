import axios from 'axios'
import { getItem, setItem, clearAuth } from './tokenStorage'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use(config => {
  const token = getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let refreshPromise = null

function refreshAccessToken() {
  if (!refreshPromise) {
    const refreshToken = getItem('refreshToken')
    refreshPromise = axios.post(`/api/auth/refresh?refreshToken=${refreshToken}`)
      .then(res => {
        const { accessToken, refreshToken: newRefreshToken } = res.data.data
        setItem('accessToken', accessToken)
        if (newRefreshToken) setItem('refreshToken', newRefreshToken)
        return accessToken
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

api.interceptors.response.use(
  res => res,
  async err => {
    const original = err.config
    if (err.response?.status === 401 && !original._retry) {
      original._retry = true
      const refreshToken = getItem('refreshToken')
      if (refreshToken) {
        try {
          const accessToken = await refreshAccessToken()
          original.headers.Authorization = `Bearer ${accessToken}`
          return api(original)
        } catch {
          clearAuth()
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(err)
  }
)

export default api
