import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axios'
import styles from './FollowListModal.module.css'

export default function FollowListModal({ userId, mode, onClose }) {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    setLoading(true)
    setError(false)
    api.get(`/users/${userId}/${mode}`)
      .then(res => setUsers(res.data.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [userId, mode])

  const title = mode === 'followers' ? '팔로워' : '팔로잉'

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={e => e.stopPropagation()}>
        <div className={styles.header}>
          <h3>{title}</h3>
          <button className={styles.closeBtn} onClick={onClose}>✕</button>
        </div>

        <div className={styles.list}>
          {loading ? (
            <p className={styles.empty}>불러오는 중...</p>
          ) : error ? (
            <p className={styles.empty}>목록을 불러오지 못했습니다.</p>
          ) : users.length === 0 ? (
            <p className={styles.empty}>{title}가 없습니다.</p>
          ) : (
            users.map(u => (
              <Link key={u.id} to={`/users/${u.id}`} className={styles.row} onClick={onClose}>
                <div className={styles.avatar}>
                  {u.profileImage ? <img src={u.profileImage} alt="" /> : <span>👤</span>}
                </div>
                <span className={styles.nickname}>{u.nickname}</span>
              </Link>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
