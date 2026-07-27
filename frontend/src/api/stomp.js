import { Client } from '@stomp/stompjs'
import { getItem } from './tokenStorage'

export function createStompClient() {
  const protocol = location.protocol === 'https:' ? 'wss://' : 'ws://'
  return new Client({
    brokerURL: `${protocol}${location.host}/ws`,
    connectHeaders: {
      Authorization: `Bearer ${getItem('accessToken')}`,
    },
    reconnectDelay: 5000,
  })
}
