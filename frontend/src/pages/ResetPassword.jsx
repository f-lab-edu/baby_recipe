import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import api from '../api/axios'
import styles from './Auth.module.css'

export default function ResetPassword() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [form, setForm] = useState({
    token: searchParams.get('token') || '',
    newPassword: '',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async e => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await api.post('/auth/password-reset/confirm', form)
      navigate('/login')
    } catch (err) {
      setError(err.response?.data?.message || '비밀번호 재설정에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.box}>
        <h2 className={styles.title}>📖 reciplog</h2>
        <p className={styles.sub}>비밀번호 재설정</p>
        <form onSubmit={handleSubmit} className={styles.form}>
          <input
            type="text" placeholder="재설정 토큰"
            value={form.token}
            onChange={e => setForm(f => ({ ...f, token: e.target.value }))}
            required
          />
          <input
            type="password" placeholder="새 비밀번호 (8자 이상)"
            value={form.newPassword}
            onChange={e => setForm(f => ({ ...f, newPassword: e.target.value }))}
            required minLength={8}
          />
          {error && <p className="error-msg">{error}</p>}
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? '처리 중...' : '비밀번호 변경'}
          </button>
        </form>
        <p className={styles.footer}>
          <Link to="/login">로그인으로 돌아가기</Link>
        </p>
      </div>
    </div>
  )
}
