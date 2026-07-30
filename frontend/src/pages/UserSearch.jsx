import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'
import { useAuth } from '../contexts/AuthContext'
import styles from './UserSearch.module.css'

export default function UserSearch() {
  const navigate = useNavigate()
  const { user: me, isLoggedIn } = useAuth()
  const [keyword, setKeyword] = useState('')
  const [users, setUsers] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)

  const search = async (k, p) => {
    if (!k.trim()) return
    setLoading(true)
    try {
      const res = await api.get('/users/search', { params: { keyword: k, page: p, size: 20 } })
      setUsers(res.data.data.content)
      setTotalPages(res.data.data.totalPages)
      setSearched(true)
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = e => {
    e.preventDefault()
    setPage(0)
    search(keyword, 0)
  }

  const changePage = p => {
    setPage(p)
    search(keyword, p)
  }

  const toggleFollow = async (targetUser) => {
    if (!isLoggedIn) { navigate('/login'); return }
    if (targetUser.following) {
      await api.delete(`/users/${targetUser.id}/follow`)
    } else {
      await api.post(`/users/${targetUser.id}/follow`)
    }
    setUsers(list => list.map(u => u.id === targetUser.id
      ? { ...u, following: !u.following, followerCount: u.followerCount + (u.following ? -1 : 1) }
      : u))
  }

  const startChat = async (targetId) => {
    if (!isLoggedIn) { navigate('/login'); return }
    try {
      const res = await api.post('/chat/rooms', { partnerId: targetId })
      navigate(`/chat/${res.data.data.id}`)
    } catch (err) {
      alert(err.response?.data?.message || '채팅을 시작할 수 없습니다.')
    }
  }

  return (
    <div className="page">
      <div className="container">
        <h1 style={{ fontSize: '22px', fontWeight: 700, marginBottom: '24px' }}>🔍 유저 검색</h1>

        <form onSubmit={handleSearch} className={styles.searchForm}>
          <input
            placeholder="닉네임으로 검색..."
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
          />
          <button type="submit" className="btn-primary">검색</button>
        </form>

        {loading ? (
          <p className={styles.loading}>불러오는 중...</p>
        ) : !searched ? (
          <p className={styles.empty}>닉네임을 입력해 다른 사용자를 찾아보세요.</p>
        ) : users.length === 0 ? (
          <p className={styles.empty}>검색 결과가 없습니다.</p>
        ) : (
          <div className={styles.list}>
            {users.map(u => (
              <div key={u.id} className={styles.row}>
                <Link to={`/users/${u.id}`} className={styles.identity}>
                  <div className={styles.avatar}>
                    {u.profileImage ? <img src={u.profileImage} alt="" /> : <span>👤</span>}
                  </div>
                  <div className={styles.info}>
                    <span className={styles.nickname}>{u.nickname}</span>
                    <span className={styles.stats}>팔로워 {u.followerCount}</span>
                  </div>
                </Link>
                {me?.id !== u.id && (
                  <div className={styles.actions}>
                    <button
                      className={u.following ? 'btn-secondary btn-sm' : 'btn-primary btn-sm'}
                      onClick={() => toggleFollow(u)}
                    >
                      {u.following ? '팔로잉' : '팔로우'}
                    </button>
                    <button className="btn-secondary btn-sm" onClick={() => startChat(u.id)}>채팅하기</button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div className={styles.pagination}>
            <button onClick={() => changePage(page - 1)} disabled={page === 0} className="btn-secondary">이전</button>
            <span>{page + 1} / {totalPages}</span>
            <button onClick={() => changePage(page + 1)} disabled={page >= totalPages - 1} className="btn-secondary">다음</button>
          </div>
        )}
      </div>
    </div>
  )
}
