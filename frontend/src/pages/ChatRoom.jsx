import { useState, useEffect, useRef } from 'react'
import { useParams } from 'react-router-dom'
import api from '../api/axios'
import { createStompClient } from '../api/stomp'
import { useAuth } from '../contexts/AuthContext'
import styles from './ChatRoom.module.css'

export default function ChatRoom() {
  const { roomId } = useParams()
  const { user } = useAuth()
  const [messages, setMessages] = useState([])
  const [partner, setPartner] = useState(null)
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const clientRef = useRef(null)
  const bottomRef = useRef(null)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.get(`/chat/rooms/${roomId}/messages`, { params: { page: 0, size: 30 } }),
      api.get('/chat/rooms'),
    ]).then(([mRes, rRes]) => {
      setMessages(mRes.data.data.content.slice().reverse())
      const room = rRes.data.data.find(r => String(r.id) === roomId)
      setPartner(room?.partner ?? null)
    }).finally(() => setLoading(false))

    const client = createStompClient()
    client.onConnect = () => {
      client.subscribe(`/topic/chat.${roomId}`, msg => {
        setMessages(prev => [...prev, JSON.parse(msg.body)])
      })
    }
    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [roomId])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = (e) => {
    e.preventDefault()
    if (!content.trim() || !clientRef.current?.connected) return
    clientRef.current.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ roomId: Number(roomId), content }),
    })
    setContent('')
  }

  if (loading) return <p style={{ textAlign: 'center', padding: '60px', color: '#868e96' }}>불러오는 중...</p>

  return (
    <div className="page">
      <div className="container">
        <div className={styles.header}>{partner?.nickname ?? '채팅'}</div>
        <div className={styles.messages}>
          {messages.map(m => (
            <div key={m.id} className={m.senderId === user?.id ? styles.myMessage : styles.theirMessage}>
              {m.content}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
        <form className={styles.form} onSubmit={sendMessage}>
          <input
            value={content}
            onChange={e => setContent(e.target.value)}
            placeholder="메시지를 입력하세요"
          />
          <button type="submit" className="btn-primary">전송</button>
        </form>
      </div>
    </div>
  )
}
