import { Client } from '@stomp/stompjs'

export function createStompClient() {
  const protocol = location.protocol === 'https:' ? 'wss://' : 'ws://'
  return new Client({
    brokerURL: `${protocol}${location.host}/ws`,
    connectHeaders: {
      Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
    },
    reconnectDelay: 5000,
  })
}
