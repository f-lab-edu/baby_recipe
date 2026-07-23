import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axios'
import styles from './ChatRoomList.module.css'

export default function ChatRoomList() {
  const [rooms, setRooms] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api.get('/chat/rooms')
      .then(res => setRooms(res.data.data))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="page">
      <div className="container">
        <h1 style={{ fontSize: '22px', fontWeight: 700, marginBottom: '24px' }}>💬 채팅</h1>
        {loading ? (
          <p className={styles.loading}>불러오는 중...</p>
        ) : rooms.length === 0 ? (
          <p className={styles.empty}>아직 대화가 없습니다.</p>
        ) : (
          <div className={styles.list}>
            {rooms.map(room => (
              <Link key={room.id} to={`/chat/${room.id}`} className={styles.room}>
                <div className={styles.avatar}>
                  {room.partner.profileImage ? <img src={room.partner.profileImage} alt="" /> : <span>👤</span>}
                </div>
                <div className={styles.info}>
                  <span className={styles.nickname}>{room.partner.nickname}</span>
                  <span className={styles.preview}>{room.lastMessage || '대화를 시작해보세요.'}</span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
