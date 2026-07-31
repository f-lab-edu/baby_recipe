import { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axios'
import styles from './Auth.module.css'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async e => {
    e.preventDefault()
    setError('')
    setMessage('')
    setLoading(true)
    try {
      await api.post('/auth/password-reset/request', { email })
      setMessage('해당 이메일로 가입된 계정이 있다면 재설정 안내를 보냈습니다.')
    } catch (err) {
      setError(err.response?.data?.message || '요청 처리에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.box}>
        <h2 className={styles.title}>📖 reciplog</h2>
        <p className={styles.sub}>비밀번호 찾기</p>
        <form onSubmit={handleSubmit} className={styles.form}>
          <input
            type="email" placeholder="가입한 이메일"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
          />
          {message && <p>{message}</p>}
          {error && <p className="error-msg">{error}</p>}
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? '처리 중...' : '재설정 링크 받기'}
          </button>
        </form>
        <p className={styles.footer}>
          토큰을 받으셨나요? <Link to="/reset-password">비밀번호 재설정</Link>
        </p>
        <p className={styles.footer}>
          <Link to="/login">로그인으로 돌아가기</Link>
        </p>
      </div>
    </div>
  )
}
