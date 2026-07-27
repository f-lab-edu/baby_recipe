const PERSIST_KEY = 'authPersist'
const AUTH_KEYS = ['accessToken', 'refreshToken', 'user']

// 로그인 상태 유지에 체크했으면 localStorage(브라우저를 닫아도 유지),
// 아니면 sessionStorage(탭/브라우저를 닫으면 사라짐)를 쓴다.
function store() {
  return localStorage.getItem(PERSIST_KEY) === 'true' ? localStorage : sessionStorage
}

export function setPersistent(persistent) {
  if (persistent) localStorage.setItem(PERSIST_KEY, 'true')
  else localStorage.removeItem(PERSIST_KEY)
}

export const getItem = key => store().getItem(key)
export const setItem = (key, value) => store().setItem(key, value)

export function clearAuth() {
  localStorage.removeItem(PERSIST_KEY)
  AUTH_KEYS.forEach(key => {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  })
}
